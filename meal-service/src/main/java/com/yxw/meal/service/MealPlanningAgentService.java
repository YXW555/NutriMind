package com.yxw.meal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.meal.client.FoodCatalogClient;
import com.yxw.meal.client.UserProfileClient;
import com.yxw.meal.client.dto.HealthGoalSnapshot;
import com.yxw.meal.client.dto.HealthProfileSnapshot;
import com.yxw.meal.client.dto.ProfileOverviewSnapshot;
import com.yxw.meal.config.RagProperties;
import com.yxw.meal.dto.GeneratedMealPlanResponse;
import com.yxw.meal.dto.GeneratedMealPlanWeekResponse;
import com.yxw.meal.dto.MealPlanGenerateDailyRequest;
import com.yxw.meal.dto.MealPlanGenerateWeekRequest;
import com.yxw.meal.dto.MealPlanItemCommand;
import com.yxw.meal.dto.MealPlanItemResponse;
import com.yxw.meal.dto.MealPlanResponse;
import com.yxw.meal.dto.MealPlanSaveRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class MealPlanningAgentService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanningAgentService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_TARGET_CALORIES = BigDecimal.valueOf(2000);
    private static final BigDecimal DEFAULT_TARGET_PROTEIN = BigDecimal.valueOf(90);

    private final RagProperties ragProperties;
    private final QwenModelStudioService qwenModelStudioService;
    private final MilvusKnowledgeStoreService milvusKnowledgeStoreService;
    private final NutritionKnowledgeBaseService nutritionKnowledgeBaseService;
    private final UserProfileClient userProfileClient;
    private final FoodCatalogClient foodCatalogClient;
    private final MealPlanService mealPlanService;
    private final ObjectMapper objectMapper;

    public MealPlanningAgentService(RagProperties ragProperties,
                                   QwenModelStudioService qwenModelStudioService,
                                   MilvusKnowledgeStoreService milvusKnowledgeStoreService,
                                   NutritionKnowledgeBaseService nutritionKnowledgeBaseService,
                                   UserProfileClient userProfileClient,
                                   FoodCatalogClient foodCatalogClient,
                                   MealPlanService mealPlanService,
                                   ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.qwenModelStudioService = qwenModelStudioService;
        this.milvusKnowledgeStoreService = milvusKnowledgeStoreService;
        this.nutritionKnowledgeBaseService = nutritionKnowledgeBaseService;
        this.userProfileClient = userProfileClient;
        this.foodCatalogClient = foodCatalogClient;
        this.mealPlanService = mealPlanService;
        this.objectMapper = objectMapper;
    }

    public Object generateDaily(Long userId, MealPlanGenerateDailyRequest request) {
        boolean saveDraft = Boolean.TRUE.equals(request.getSaveDraft());
        PlanContext context = loadPlanContext();
        JsonNode draft = callPlannerModel(buildDailyPlannerPrompt(context, request.getPlanDate(), request.getPreference()));
        List<MealPlanItemCommand> commands = toCommands(draft);

        MealPlanSaveRequest saveRequest = new MealPlanSaveRequest();
        saveRequest.setPlanDate(request.getPlanDate());
        saveRequest.setTitle(textOrNull(draft, "title"));
        saveRequest.setNotes(textOrNull(draft, "notes"));
        saveRequest.setItems(commands);

        if (saveDraft) {
            MealPlanResponse saved = mealPlanService.saveDailyPlan(userId, saveRequest);
            return saved;
        }

        GeneratedMealPlanResponse generated = buildGeneratedResponse(context, request.getPlanDate(), saveRequest);
        return generated;
    }

    public GeneratedMealPlanWeekResponse generateWeek(Long userId, MealPlanGenerateWeekRequest request) {
        boolean saveDraft = Boolean.TRUE.equals(request.getSaveDraft());
        LocalDate weekStart = request.getAnchorDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<GeneratedMealPlanResponse> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            MealPlanGenerateDailyRequest dailyRequest = new MealPlanGenerateDailyRequest();
            dailyRequest.setPlanDate(day);
            dailyRequest.setPreference(request.getPreference());
            dailyRequest.setSaveDraft(saveDraft);

            Object response = generateDaily(userId, dailyRequest);
            if (response instanceof MealPlanResponse mealPlanResponse) {
                // When saving, return a light-weight view for week list to keep payload reasonable.
                days.add(GeneratedMealPlanResponse.builder()
                        .planDate(day)
                        .title(mealPlanResponse.getTitle())
                        .notes(mealPlanResponse.getNotes())
                        .totalCalories(zeroSafe(mealPlanResponse.getTotalCalories()))
                        .totalProtein(zeroSafe(mealPlanResponse.getTotalProtein()))
                        .totalFat(zeroSafe(mealPlanResponse.getTotalFat()))
                        .totalCarbohydrate(zeroSafe(mealPlanResponse.getTotalCarbohydrate()))
                        .items(mealPlanResponse.getItems() == null ? List.of() : mealPlanResponse.getItems())
                        .build());
            } else if (response instanceof GeneratedMealPlanResponse generated) {
                days.add(generated);
            }
        }

        return GeneratedMealPlanWeekResponse.builder()
                .weekStart(weekStart)
                .days(days)
                .build();
    }

    private PlanContext loadPlanContext() {
        try {
            ProfileOverviewSnapshot overview = userProfileClient.getOverview();
            HealthGoalSnapshot goal = overview == null ? null : overview.getHealthGoal();
            HealthProfileSnapshot healthProfile = overview == null ? null : overview.getHealthProfile();
            return new PlanContext(
                    overview == null ? null : firstNonBlank(overview.getNickname(), overview.getUsername()),
                    goal == null ? null : goal.getGoalType(),
                    firstNonNull(goal == null ? null : goal.getTargetCalories(), DEFAULT_TARGET_CALORIES),
                    firstNonNull(goal == null ? null : goal.getTargetProtein(), DEFAULT_TARGET_PROTEIN),
                    healthProfile == null ? null : healthProfile.getDietaryPreference(),
                    healthProfile == null ? null : healthProfile.getAllergies(),
                    healthProfile == null ? null : healthProfile.getMedicalNotes()
            );
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Meal planning fallback: failed to load profile overview", exception);
            return new PlanContext(null, null, DEFAULT_TARGET_CALORIES, DEFAULT_TARGET_PROTEIN, null, null, null);
        }
    }

    private String buildDailyPlannerPrompt(PlanContext context, LocalDate planDate, String preference) {
        String query = "一日三餐 饮食计划 " + resolveGoalLabel(context.goalType()) + " " + nullToEmpty(preference);
        List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits = searchKnowledge(query, 4);

        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是 NutriMind 的智能饮食规划助手。你需要生成一份可执行的一日三餐计划（可以包含 0-1 次加餐），并输出严格 JSON。

                约束与原则：
                1) 结果必须是 JSON（不要 Markdown，不要多余文本）。
                2) 每个 item 必须包含：food_keyword, meal_type, quantity_grams（数字）, note（可选）。
                3) meal_type 只能是：BREAKFAST / LUNCH / DINNER / SNACK。
                4) quantity_grams 以克为单位，建议 50-400 范围；无法确定时用 100。
                5) 计划要尽量贴近目标热量/蛋白，但不要胡编具体医学结论；有过敏/忌口要避开。
                6) 优先选择常见食物（鸡胸肉/鸡蛋/牛奶/酸奶/米饭/燕麦/豆腐/鱼/西兰花/水果等）。

                输出 JSON schema（示例结构）：
                {
                  "title": "string",
                  "notes": "string",
                  "items": [
                    {"food_keyword":"鸡胸肉","meal_type":"LUNCH","quantity_grams":150,"note":"清蒸/少油"}
                  ]
                }
                """);

        builder.append("\n\n【日期】").append(planDate);
        if (StringUtils.hasText(context.displayName())) {
            builder.append("\n【称呼】").append(context.displayName());
        }
        builder.append("\n【目标】").append(resolveGoalLabel(context.goalType()));
        builder.append("\n【目标热量】").append(formatDecimal(context.targetCalories())).append(" 千卡");
        builder.append("\n【目标蛋白】").append(formatDecimal(context.targetProtein())).append(" 克");
        if (StringUtils.hasText(context.dietaryPreference())) {
            builder.append("\n【饮食偏好】").append(context.dietaryPreference());
        }
        if (StringUtils.hasText(context.allergies())) {
            builder.append("\n【过敏/不耐受】").append(context.allergies());
        }
        if (StringUtils.hasText(context.medicalNotes())) {
            builder.append("\n【健康备注】").append(limit(context.medicalNotes(), 140));
        }
        if (StringUtils.hasText(preference)) {
            builder.append("\n【额外偏好】").append(preference.trim());
        }

        if (!knowledgeHits.isEmpty()) {
            builder.append("\n\n【营养知识要点】\n");
            for (int i = 0; i < knowledgeHits.size(); i++) {
                var hit = knowledgeHits.get(i);
                builder.append(i + 1).append(". ").append(hit.summary()).append("\n");
            }
        }

        return builder.toString();
    }

    private List<NutritionKnowledgeBaseService.KnowledgeHit> searchKnowledge(String query, int limit) {
        if (milvusKnowledgeStoreService.isReady()) {
            List<NutritionKnowledgeBaseService.KnowledgeHit> hits = milvusKnowledgeStoreService.search(query, limit);
            if (!hits.isEmpty()) {
                return hits;
            }
        }
        return nutritionKnowledgeBaseService.search(query, limit);
    }

    private JsonNode callPlannerModel(String prompt) {
        if (!ragProperties.isEnabled() || !qwenModelStudioService.isReady()) {
            throw new IllegalStateException("planner model is not ready. Please configure APP_RAG_QWEN_API_KEY.");
        }

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(ragProperties.getQwen().getBaseUrl())
                .apiKey(ragProperties.getQwen().getApiKey().trim())
                .modelName(ragProperties.getQwen().getChatModel())
                .temperature(ragProperties.getQwen().getTemperature())
                .strictJsonSchema(true)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("你是一个严谨的饮食规划 JSON 生成器。"),
                        UserMessage.from(prompt)
                )
                .responseFormat(ResponseFormat.JSON)
                .build();

        ChatResponse response = model.chat(request);
        String text = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("planner returned empty response");
        }

        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException exception) {
            log.warn("Planner returned invalid JSON, raw: {}", limit(text, 400));
            throw new IllegalStateException("planner returned invalid JSON");
        }
    }

    private List<MealPlanItemCommand> toCommands(JsonNode draft) {
        if (draft == null || !draft.has("items") || !draft.get("items").isArray()) {
            return List.of();
        }

        List<MealPlanItemCommand> commands = new ArrayList<>();
        int sortOrder = 0;
        for (JsonNode item : draft.get("items")) {
            String keyword = textOrNull(item, "food_keyword");
            FoodNutritionSnapshot food = foodCatalogClient.pickBestMatch(keyword);
            if (food == null || food.getId() == null) {
                continue;
            }

            MealPlanItemCommand command = new MealPlanItemCommand();
            command.setFoodId(food.getId());
            command.setMealType(resolveMealType(textOrNull(item, "meal_type")));
            command.setQuantity(parseQuantity(item));
            command.setNote(textOrNull(item, "note"));
            command.setSortOrder(sortOrder++);
            commands.add(command);
        }

        // Ensure we always have at least breakfast/lunch/dinner for demo friendliness.
        Set<String> mealTypes = commands.stream().map(MealPlanItemCommand::getMealType).filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (!mealTypes.contains("BREAKFAST") || !mealTypes.contains("LUNCH") || !mealTypes.contains("DINNER")) {
            // If model produced weird distribution, keep top items but normalize missing meal types.
            commands.sort(Comparator.comparing(MealPlanItemCommand::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
            int index = 0;
            if (!mealTypes.contains("BREAKFAST") && index < commands.size()) commands.get(index++).setMealType("BREAKFAST");
            if (!mealTypes.contains("LUNCH") && index < commands.size()) commands.get(index++).setMealType("LUNCH");
            if (!mealTypes.contains("DINNER") && index < commands.size()) commands.get(index++).setMealType("DINNER");
        }

        return commands;
    }

    private GeneratedMealPlanResponse buildGeneratedResponse(PlanContext context, LocalDate planDate, MealPlanSaveRequest saveRequest) {
        List<MealPlanItemResponse> items = new ArrayList<>();
        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        BigDecimal totalCarbohydrate = BigDecimal.ZERO;

        List<MealPlanItemCommand> commands = saveRequest.getItems() == null ? Collections.emptyList() : saveRequest.getItems();
        for (MealPlanItemCommand command : commands) {
            FoodNutritionSnapshot food = foodCatalogClient.getFoodById(command.getFoodId());
            BigDecimal ratio = zeroSafe(command.getQuantity()).divide(HUNDRED, 4, RoundingMode.HALF_UP);

            BigDecimal calories = multiply(food.getCalories(), ratio);
            BigDecimal protein = multiply(food.getProtein(), ratio);
            BigDecimal fat = multiply(food.getFat(), ratio);
            BigDecimal carb = multiply(food.getCarbohydrate(), ratio);

            items.add(MealPlanItemResponse.builder()
                    .id(null)
                    .foodId(food.getId())
                    .foodName(food.getName())
                    .mealType(resolveMealType(command.getMealType()))
                    .quantity(zeroSafe(command.getQuantity()))
                    .note(command.getNote())
                    .sortOrder(command.getSortOrder())
                    .calories(calories)
                    .protein(protein)
                    .fat(fat)
                    .carbohydrate(carb)
                    .build());

            totalCalories = totalCalories.add(calories);
            totalProtein = totalProtein.add(protein);
            totalFat = totalFat.add(fat);
            totalCarbohydrate = totalCarbohydrate.add(carb);
        }

        return GeneratedMealPlanResponse.builder()
                .planDate(planDate)
                .title(saveRequest.getTitle())
                .notes(saveRequest.getNotes())
                .targetCalories(context.targetCalories())
                .targetProtein(context.targetProtein())
                .totalCalories(totalCalories.setScale(2, RoundingMode.HALF_UP))
                .totalProtein(totalProtein.setScale(2, RoundingMode.HALF_UP))
                .totalFat(totalFat.setScale(2, RoundingMode.HALF_UP))
                .totalCarbohydrate(totalCarbohydrate.setScale(2, RoundingMode.HALF_UP))
                .items(items)
                .build();
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        return source.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseQuantity(JsonNode item) {
        JsonNode quantityNode = item.get("quantity_grams");
        if (quantityNode == null || !quantityNode.isNumber()) {
            return BigDecimal.valueOf(100);
        }
        double value = quantityNode.asDouble(100D);
        double clamped = Math.max(20D, Math.min(600D, value));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveMealType(String mealType) {
        if (!StringUtils.hasText(mealType)) {
            return "SNACK";
        }
        String normalized = mealType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BREAKFAST", "LUNCH", "DINNER", "SNACK" -> normalized;
            default -> "SNACK";
        };
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLen) {
        if (!StringUtils.hasText(value) || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal fallback) {
        return first != null ? first : fallback;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private BigDecimal zeroSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatDecimal(BigDecimal value) {
        BigDecimal safe = zeroSafe(value);
        if (safe.stripTrailingZeros().scale() <= 0) {
            return safe.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return safe.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String resolveGoalLabel(String goalType) {
        if (!StringUtils.hasText(goalType)) {
            return "均衡饮食";
        }
        return switch (goalType.trim().toUpperCase(Locale.ROOT)) {
            case "FAT_LOSS" -> "减脂";
            case "MUSCLE_GAIN" -> "增肌";
            case "MAINTAIN" -> "维持体重";
            default -> "均衡饮食";
        };
    }

    private record PlanContext(
            String displayName,
            String goalType,
            BigDecimal targetCalories,
            BigDecimal targetProtein,
            String dietaryPreference,
            String allergies,
            String medicalNotes
    ) {
    }
}
