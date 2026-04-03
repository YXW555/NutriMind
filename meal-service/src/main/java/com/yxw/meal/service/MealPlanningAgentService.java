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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MealPlanningAgentService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanningAgentService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_TARGET_CALORIES = BigDecimal.valueOf(2000);
    private static final BigDecimal DEFAULT_TARGET_PROTEIN = BigDecimal.valueOf(90);
    private static final String MODE_AI_AGENT = "AI_AGENT";
    private static final String MODE_RULE_BASED = "RULE_BASED";
    private static final String MODE_MIXED = "MIXED";

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
        PlanningDraft draft = generatePlanningDraft(context, request.getPlanDate(), request.getPreference(), 0, List.of());
        MealPlanSaveRequest saveRequest = toSaveRequest(request.getPlanDate(), draft);

        if (saveDraft) {
            return mealPlanService.saveDailyPlan(userId, saveRequest);
        }

        return buildGeneratedResponse(context, request.getPlanDate(), saveRequest, draft);
    }

    public GeneratedMealPlanWeekResponse generateWeek(Long userId, MealPlanGenerateWeekRequest request) {
        boolean saveDraft = Boolean.TRUE.equals(request.getSaveDraft());
        PlanContext context = loadPlanContext();
        LocalDate weekStart = request.getAnchorDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<GeneratedMealPlanResponse> days = new ArrayList<>();
        List<String> recentFoodNames = new ArrayList<>();
        Set<String> modes = new LinkedHashSet<>();
        List<String> weekTips = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate planDate = weekStart.plusDays(i);
            PlanningDraft draft = generatePlanningDraft(context, planDate, request.getPreference(), i, recentFoodNames);
            MealPlanSaveRequest saveRequest = toSaveRequest(planDate, draft);
            if (saveDraft) {
                mealPlanService.saveDailyPlan(userId, saveRequest);
            }

            GeneratedMealPlanResponse dayResponse = buildGeneratedResponse(context, planDate, saveRequest, draft);
            days.add(dayResponse);
            modes.add(dayResponse.getGenerationMode());
            if (dayResponse.getTips() != null) {
                weekTips.addAll(dayResponse.getTips());
            }
            rememberFoods(recentFoodNames, dayResponse);
        }

        return GeneratedMealPlanWeekResponse.builder()
                .weekStart(weekStart)
                .generationMode(resolveWeekGenerationMode(modes))
                .summary(buildWeekSummary(context, days))
                .tips(uniqueStrings(weekTips, 4))
                .days(days)
                .build();
    }

    private PlanningDraft generatePlanningDraft(PlanContext context,
                                                LocalDate planDate,
                                                String preference,
                                                int dayIndex,
                                                List<String> recentFoodNames) {
        CandidateFoodPool pool = buildCandidateFoodPool(context, preference);
        List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits = searchKnowledge(buildKnowledgeQuery(context, preference), 4);
        PlanningDraft aiDraft = tryGenerateWithAi(context, planDate, preference, pool, knowledgeHits, recentFoodNames);
        if (isUsableDraft(aiDraft)) {
            return aiDraft;
        }
        return buildRuleBasedDraft(context, planDate, preference, dayIndex, pool, knowledgeHits, recentFoodNames);
    }

    private PlanningDraft tryGenerateWithAi(PlanContext context,
                                            LocalDate planDate,
                                            String preference,
                                            CandidateFoodPool pool,
                                            List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits,
                                            List<String> recentFoodNames) {
        if (!ragProperties.isEnabled() || !qwenModelStudioService.isReady()) {
            return null;
        }

        try {
            String prompt = buildDailyPlannerPrompt(context, planDate, preference, pool, knowledgeHits, recentFoodNames);
            JsonNode draft = callPlannerModel(prompt);
            return toPlanningDraft(draft, context, planDate, pool, knowledgeHits);
        } catch (Exception exception) {
            log.warn("Meal planning agent fallback to rule-based generator", exception);
            return null;
        }
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

    private PlanningDraft toPlanningDraft(JsonNode draft,
                                          PlanContext context,
                                          LocalDate planDate,
                                          CandidateFoodPool pool,
                                          List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        if (draft == null || !draft.has("items") || !draft.get("items").isArray()) {
            return null;
        }

        List<PlannedItem> items = new ArrayList<>();
        Set<Long> usedFoodIds = new LinkedHashSet<>();
        int sortOrder = 0;
        for (JsonNode item : draft.get("items")) {
            FoodNutritionSnapshot food = matchFood(textOrNull(item, "food_keyword"), pool);
            if (food == null || food.getId() == null || usedFoodIds.contains(food.getId())) {
                continue;
            }
            items.add(new PlannedItem(
                    food.getId(),
                    food.getName(),
                    resolveMealType(textOrNull(item, "meal_type")),
                    parseQuantity(item),
                    trimToNull(textOrNull(item, "note")),
                    sortOrder++
            ));
            usedFoodIds.add(food.getId());
        }

        if (!containsMainMeals(items)) {
            return null;
        }

        items.sort(Comparator.comparing(PlannedItem::mealType, this::compareMealType)
                .thenComparingInt(PlannedItem::sortOrder));

        return new PlanningDraft(
                MODE_AI_AGENT,
                firstNonBlank(textOrNull(draft, "title"), buildPlanTitle(context, planDate, true)),
                firstNonBlank(textOrNull(draft, "notes"), buildPlanNotes(context, null, true)),
                firstNonBlank(textOrNull(draft, "summary"), buildPlanSummary(context, items, true)),
                uniqueStrings(readStringList(draft, "tips"), 3),
                uniqueStrings(readStringList(draft, "warnings"), 3),
                toReferenceList(knowledgeHits),
                items
        );
    }

    private PlanningDraft buildRuleBasedDraft(PlanContext context,
                                              LocalDate planDate,
                                              String preference,
                                              int dayIndex,
                                              CandidateFoodPool pool,
                                              List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits,
                                              List<String> recentFoodNames) {
        Set<Long> usedFoodIds = new LinkedHashSet<>();
        List<PlannedItem> items = new ArrayList<>();

        BigDecimal calorieFactor = clampScale(zeroSafe(context.targetCalories()).divide(DEFAULT_TARGET_CALORIES, 4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.85), BigDecimal.valueOf(1.2));
        BigDecimal proteinFactor = clampScale(zeroSafe(context.targetProtein()).divide(DEFAULT_TARGET_PROTEIN, 4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.9), BigDecimal.valueOf(1.3));

        FoodNutritionSnapshot breakfastStaple = chooseFood(pool.breakfastStaples(), recentFoodNames, usedFoodIds, dayIndex);
        FoodNutritionSnapshot breakfastProtein = chooseFood(pool.breakfastProteins(), recentFoodNames, usedFoodIds, dayIndex + 1);
        FoodNutritionSnapshot breakfastFruit = chooseFood(pool.fruits(), recentFoodNames, usedFoodIds, dayIndex + 2);

        addItem(items, breakfastStaple, "BREAKFAST",
                stapleQuantity(breakfastStaple, calorieFactor, "BREAKFAST", context.goalType()),
                "早餐先补足复合碳水，减少上午饥饿感。");
        addItem(items, breakfastProtein, "BREAKFAST",
                proteinQuantity(breakfastProtein, proteinFactor, "BREAKFAST"),
                "早餐补一点蛋白，饱腹感和稳定性会更好。");
        addItem(items, breakfastFruit, "BREAKFAST",
                fruitQuantity(breakfastFruit),
                "用水果补充纤维和维生素。");

        addMainMeal(items, pool, recentFoodNames, usedFoodIds, dayIndex + 3, context, calorieFactor, proteinFactor, "LUNCH");
        addMainMeal(items, pool, recentFoodNames, usedFoodIds, dayIndex + 6, context, calorieFactor, proteinFactor, "DINNER");

        if (shouldAddSnack(context, preference)) {
            FoodNutritionSnapshot snack = chooseFood(pool.snacks(), recentFoodNames, usedFoodIds, dayIndex + 9);
            addItem(items, snack, "SNACK", snackQuantity(snack, context.goalType()),
                    "加餐以高蛋白或低负担食物为主。");
        }

        if (!containsMainMeals(items)) {
            throw new IllegalStateException("Failed to build a usable meal plan from local food pool.");
        }

        return new PlanningDraft(
                MODE_RULE_BASED,
                buildPlanTitle(context, planDate, false),
                buildPlanNotes(context, preference, false),
                buildPlanSummary(context, items, false),
                buildPlanTips(context, preference, knowledgeHits),
                buildWarnings(context),
                toReferenceList(knowledgeHits),
                reIndex(items)
        );
    }

    private void addMainMeal(List<PlannedItem> items,
                             CandidateFoodPool pool,
                             List<String> recentFoodNames,
                             Set<Long> usedFoodIds,
                             int offset,
                             PlanContext context,
                             BigDecimal calorieFactor,
                             BigDecimal proteinFactor,
                             String mealType) {
        FoodNutritionSnapshot protein = chooseFood(pool.proteins(), recentFoodNames, usedFoodIds, offset);
        FoodNutritionSnapshot carb = chooseFood(pool.carbs(), recentFoodNames, usedFoodIds, offset + 1);
        FoodNutritionSnapshot vegetable = chooseFood(pool.vegetables(), recentFoodNames, usedFoodIds, offset + 2);

        addItem(items, protein, mealType, proteinQuantity(protein, proteinFactor, mealType),
                "DINNER".equals(mealType) ? "晚餐保留蛋白，帮助恢复又不容易饿。" : "午餐优先保证优质蛋白。");
        addItem(items, carb, mealType, stapleQuantity(carb, calorieFactor, mealType, context.goalType()),
                "DINNER".equals(mealType) ? "晚餐主食略收一点，更贴近目标。" : "主食按目标控制分量，避免下午能量不足。");
        addItem(items, vegetable, mealType, vegetableQuantity(),
                "搭配蔬菜增加体积感和饱腹感。");
    }

    private CandidateFoodPool buildCandidateFoodPool(PlanContext context, String preference) {
        String constraints = (nullToEmpty(context.dietaryPreference()) + " " + nullToEmpty(context.allergies()) + " " + nullToEmpty(preference))
                .toLowerCase(Locale.ROOT);
        Predicate<FoodNutritionSnapshot> filter = food -> isAllowedFood(food, constraints);

        List<FoodNutritionSnapshot> breakfastStaples = loadFoods(List.of("燕麦片", "全麦面包", "面包", "玉米", "馒头"), filter);
        List<FoodNutritionSnapshot> breakfastProteins = loadFoods(List.of("鸡蛋", "希腊酸奶", "酸奶", "纯牛奶", "牛奶", "豆腐"), filter);
        List<FoodNutritionSnapshot> proteins = loadFoods(List.of("鸡胸肉", "牛肉", "鱼", "虾", "三文鱼", "豆腐", "北豆腐", "鸡肉沙拉", "番茄炒蛋"), filter);
        List<FoodNutritionSnapshot> carbs = loadFoods(List.of("米饭", "玉米", "土豆", "面条", "馒头", "全麦面包", "燕麦片", "炒饭", "炒面"), filter);
        List<FoodNutritionSnapshot> vegetables = loadFoods(List.of("西兰花", "黄瓜", "番茄", "清炒时蔬", "沙拉", "酸辣土豆丝"), filter);
        List<FoodNutritionSnapshot> fruits = loadFoods(List.of("香蕉", "苹果", "橙子", "牛油果"), filter);
        List<FoodNutritionSnapshot> snacks = loadFoods(List.of("希腊酸奶", "酸奶", "纯牛奶", "牛奶", "香蕉", "苹果", "橙子", "鸡蛋"), filter);

        return new CandidateFoodPool(
                ensureNotEmpty(breakfastStaples, List.of("燕麦片", "全麦面包", "玉米")),
                ensureNotEmpty(breakfastProteins, List.of("鸡蛋", "酸奶", "牛奶", "豆腐")),
                ensureNotEmpty(proteins, List.of("鸡胸肉", "豆腐", "牛肉", "鱼")),
                ensureNotEmpty(carbs, List.of("米饭", "燕麦片", "全麦面包", "土豆")),
                ensureNotEmpty(vegetables, List.of("西兰花", "黄瓜", "番茄", "沙拉")),
                ensureNotEmpty(fruits, List.of("香蕉", "苹果", "橙子")),
                ensureNotEmpty(snacks, List.of("酸奶", "苹果", "牛奶", "香蕉"))
        );
    }

    private List<FoodNutritionSnapshot> loadFoods(List<String> keywords, Predicate<FoodNutritionSnapshot> filter) {
        Map<Long, FoodNutritionSnapshot> unique = new LinkedHashMap<>();
        for (String keyword : keywords) {
            FoodNutritionSnapshot food = foodCatalogClient.pickBestMatch(keyword);
            if (food == null || food.getId() == null || !filter.test(food)) {
                continue;
            }
            unique.putIfAbsent(food.getId(), food);
        }
        return new ArrayList<>(unique.values());
    }

    private List<FoodNutritionSnapshot> ensureNotEmpty(List<FoodNutritionSnapshot> foods, List<String> fallbacks) {
        if (!foods.isEmpty()) {
            return foods;
        }
        return loadFoods(fallbacks, food -> true);
    }

    private boolean isAllowedFood(FoodNutritionSnapshot food, String constraints) {
        if (food == null) {
            return false;
        }
        String name = nullToEmpty(food.getName()).toLowerCase(Locale.ROOT);
        if (containsAny(constraints, List.of("素食", "纯素", "vegetarian", "vegan"))
                && containsAny(name, List.of("鸡", "牛肉", "鱼", "虾", "三文鱼"))) {
            return false;
        }
        if (containsAny(constraints, List.of("乳糖", "牛奶", "奶制品", "milk"))
                && containsAny(name, List.of("牛奶", "酸奶", "希腊酸奶"))) {
            return false;
        }
        if (containsAny(constraints, List.of("鸡蛋", "蛋类", "egg")) && name.contains("蛋")) {
            return false;
        }
        if (containsAny(constraints, List.of("海鲜", "虾", "鱼"))
                && containsAny(name, List.of("鱼", "虾", "三文鱼"))) {
            return false;
        }
        return true;
    }

    private String buildDailyPlannerPrompt(PlanContext context,
                                           LocalDate planDate,
                                           String preference,
                                           CandidateFoodPool pool,
                                           List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits,
                                           List<String> recentFoodNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是 NutriMind 的智能饮食规划 agent。请为用户生成一份可执行的一日饮食计划，并输出严格 JSON。

                要求：
                1) 只能输出 JSON，不要 Markdown，不要解释文字。
                2) 每个 item 必须包含：food_keyword, meal_type, quantity_grams, note。
                3) meal_type 只能是 BREAKFAST / LUNCH / DINNER / SNACK。
                4) 至少覆盖早餐、午餐、晚餐，可以额外包含 0-1 个加餐。
                5) food_keyword 优先从给定食物池中选择，避免输出食物池之外的内容。

                输出 JSON schema：
                {
                  "title": "string",
                  "notes": "string",
                  "summary": "一句话概括为什么这样安排",
                  "tips": ["string"],
                  "warnings": ["string"],
                  "items": [
                    {"food_keyword":"鸡胸肉","meal_type":"LUNCH","quantity_grams":150,"note":"清蒸/少油"}
                  ]
                }
                """);

        builder.append("\n【日期】").append(planDate);
        builder.append("\n【目标】").append(resolveGoalLabel(context.goalType()));
        builder.append("\n【目标热量】").append(formatDecimal(context.targetCalories())).append(" 千卡");
        builder.append("\n【目标蛋白】").append(formatDecimal(context.targetProtein())).append(" 克");
        if (StringUtils.hasText(context.displayName())) {
            builder.append("\n【称呼】").append(context.displayName());
        }
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
            builder.append("\n【额外要求】").append(preference.trim());
        }
        if (!recentFoodNames.isEmpty()) {
            builder.append("\n【最近几天尽量少重复】").append(String.join("、", uniqueStrings(recentFoodNames, 8)));
        }

        builder.append("\n\n【早餐候选】").append(joinFoodNames(pool.breakfastStaples(), pool.breakfastProteins(), pool.fruits()));
        builder.append("\n【主蛋白候选】").append(joinFoodNames(pool.proteins()));
        builder.append("\n【主食候选】").append(joinFoodNames(pool.carbs()));
        builder.append("\n【蔬菜候选】").append(joinFoodNames(pool.vegetables()));
        builder.append("\n【加餐候选】").append(joinFoodNames(pool.snacks()));

        if (!knowledgeHits.isEmpty()) {
            builder.append("\n\n【营养知识要点】\n");
            for (int i = 0; i < knowledgeHits.size(); i++) {
                builder.append(i + 1).append(". ").append(knowledgeHits.get(i).summary()).append("\n");
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
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(ragProperties.getQwen().getBaseUrl())
                .apiKey(ragProperties.getQwen().getApiKey().trim())
                .modelName(ragProperties.getQwen().getChatModel())
                .temperature(ragProperties.getQwen().getTemperature())
                .strictJsonSchema(true)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("你是一个严谨、实用、关注可执行性的饮食规划 JSON 生成器。"),
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
            throw new IllegalStateException("planner returned invalid JSON", exception);
        }
    }

    private GeneratedMealPlanResponse buildGeneratedResponse(PlanContext context,
                                                             LocalDate planDate,
                                                             MealPlanSaveRequest saveRequest,
                                                             PlanningDraft draft) {
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

        BigDecimal finalCalories = totalCalories.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalProtein = totalProtein.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalFat = totalFat.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalCarbohydrate = totalCarbohydrate.setScale(2, RoundingMode.HALF_UP);
        BigDecimal calorieGap = finalCalories.subtract(zeroSafe(context.targetCalories())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal proteinGap = finalProtein.subtract(zeroSafe(context.targetProtein())).setScale(2, RoundingMode.HALF_UP);

        return GeneratedMealPlanResponse.builder()
                .planDate(planDate)
                .title(saveRequest.getTitle())
                .notes(saveRequest.getNotes())
                .generationMode(draft.generationMode())
                .summary(draft.summary())
                .targetCalories(context.targetCalories())
                .targetProtein(context.targetProtein())
                .totalCalories(finalCalories)
                .totalProtein(finalProtein)
                .totalFat(finalFat)
                .totalCarbohydrate(finalCarbohydrate)
                .calorieGap(calorieGap)
                .proteinGap(proteinGap)
                .tips(uniqueStrings(draft.tips(), 3))
                .warnings(mergeWarnings(draft.warnings(), context, calorieGap, proteinGap))
                .references(uniqueStrings(draft.references(), 4))
                .items(items)
                .build();
    }

    private MealPlanSaveRequest toSaveRequest(LocalDate planDate, PlanningDraft draft) {
        MealPlanSaveRequest saveRequest = new MealPlanSaveRequest();
        saveRequest.setPlanDate(planDate);
        saveRequest.setTitle(draft.title());
        saveRequest.setNotes(draft.notes());
        saveRequest.setItems(draft.items().stream().map(item -> {
            MealPlanItemCommand command = new MealPlanItemCommand();
            command.setFoodId(item.foodId());
            command.setMealType(item.mealType());
            command.setQuantity(item.quantity());
            command.setNote(item.note());
            command.setSortOrder(item.sortOrder());
            return command;
        }).toList());
        return saveRequest;
    }

    private FoodNutritionSnapshot matchFood(String keyword, CandidateFoodPool pool) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (FoodNutritionSnapshot food : pool.allFoods()) {
            String name = nullToEmpty(food.getName()).trim().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.contains(normalized) || normalized.contains(name)) {
                return food;
            }
        }
        return foodCatalogClient.pickBestMatch(keyword.trim());
    }

    private boolean isUsableDraft(PlanningDraft draft) {
        return draft != null && draft.items() != null && draft.items().size() >= 6 && containsMainMeals(draft.items());
    }

    private boolean containsMainMeals(List<PlannedItem> items) {
        Set<String> mealTypes = items.stream()
                .map(PlannedItem::mealType)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return mealTypes.contains("BREAKFAST") && mealTypes.contains("LUNCH") && mealTypes.contains("DINNER");
    }

    private void addItem(List<PlannedItem> items,
                         FoodNutritionSnapshot food,
                         String mealType,
                         BigDecimal quantity,
                         String note) {
        if (food == null || food.getId() == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        items.add(new PlannedItem(
                food.getId(),
                food.getName(),
                mealType,
                quantity.setScale(2, RoundingMode.HALF_UP),
                trimToNull(note),
                items.size()
        ));
    }

    private FoodNutritionSnapshot chooseFood(List<FoodNutritionSnapshot> options,
                                             List<String> recentFoodNames,
                                             Set<Long> usedFoodIds,
                                             int offset) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        List<FoodNutritionSnapshot> rotated = rotate(options, offset);
        for (FoodNutritionSnapshot option : rotated) {
            if (!usedFoodIds.contains(option.getId()) && !containsFoodName(recentFoodNames, option.getName())) {
                usedFoodIds.add(option.getId());
                return option;
            }
        }
        for (FoodNutritionSnapshot option : rotated) {
            if (!usedFoodIds.contains(option.getId())) {
                usedFoodIds.add(option.getId());
                return option;
            }
        }
        FoodNutritionSnapshot fallback = rotated.get(0);
        usedFoodIds.add(fallback.getId());
        return fallback;
    }

    private <T> List<T> rotate(List<T> source, int offset) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int size = source.size();
        int normalized = Math.floorMod(offset, size);
        List<T> rotated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rotated.add(source.get((normalized + i) % size));
        }
        return rotated;
    }

    private BigDecimal stapleQuantity(FoodNutritionSnapshot food, BigDecimal scale, String mealType, String goalType) {
        if (food == null) {
            return BigDecimal.ZERO;
        }
        String name = nullToEmpty(food.getName());
        BigDecimal base;
        if (name.contains("燕麦")) {
            base = "BREAKFAST".equals(mealType) ? BigDecimal.valueOf(70) : BigDecimal.valueOf(80);
        } else if (name.contains("面包") || name.contains("馒头")) {
            base = BigDecimal.valueOf(100);
        } else if (name.contains("米饭")) {
            base = "DINNER".equals(mealType) ? BigDecimal.valueOf(120) : BigDecimal.valueOf(150);
        } else if (name.contains("玉米") || name.contains("土豆")) {
            base = BigDecimal.valueOf(160);
        } else {
            base = "DINNER".equals(mealType) ? BigDecimal.valueOf(120) : BigDecimal.valueOf(140);
        }
        if ("FAT_LOSS".equalsIgnoreCase(goalType) && "DINNER".equals(mealType)) {
            base = base.multiply(BigDecimal.valueOf(0.85));
        }
        if ("MUSCLE_GAIN".equalsIgnoreCase(goalType)) {
            base = base.multiply(BigDecimal.valueOf(1.08));
        }
        return clampScale(base.multiply(scale), BigDecimal.valueOf(60), BigDecimal.valueOf(220));
    }

    private BigDecimal proteinQuantity(FoodNutritionSnapshot food, BigDecimal scale, String mealType) {
        if (food == null) {
            return BigDecimal.ZERO;
        }
        String name = nullToEmpty(food.getName());
        BigDecimal base;
        if (name.contains("鸡蛋")) {
            base = BigDecimal.valueOf(100);
        } else if (name.contains("酸奶")) {
            base = BigDecimal.valueOf(180);
        } else if (name.contains("牛奶")) {
            base = BigDecimal.valueOf(250);
        } else if (name.contains("豆腐")) {
            base = "BREAKFAST".equals(mealType) ? BigDecimal.valueOf(120) : BigDecimal.valueOf(180);
        } else if (name.contains("鸡胸") || name.contains("牛肉") || name.contains("鱼") || name.contains("虾") || name.contains("三文鱼")) {
            base = "DINNER".equals(mealType) ? BigDecimal.valueOf(150) : BigDecimal.valueOf(170);
        } else {
            base = "BREAKFAST".equals(mealType) ? BigDecimal.valueOf(120) : BigDecimal.valueOf(150);
        }
        return clampScale(base.multiply(scale), BigDecimal.valueOf(80), BigDecimal.valueOf(260));
    }

    private BigDecimal fruitQuantity(FoodNutritionSnapshot food) {
        if (food == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(food.getName() != null && food.getName().contains("牛油") ? 80 : 150);
    }

    private BigDecimal vegetableQuantity() {
        return BigDecimal.valueOf(180);
    }

    private BigDecimal snackQuantity(FoodNutritionSnapshot food, String goalType) {
        if (food == null) {
            return BigDecimal.ZERO;
        }
        String name = nullToEmpty(food.getName());
        BigDecimal base = name.contains("牛奶") ? BigDecimal.valueOf(250) : BigDecimal.valueOf(150);
        if ("MUSCLE_GAIN".equalsIgnoreCase(goalType)) {
            base = base.multiply(BigDecimal.valueOf(1.1));
        }
        return clampScale(base, BigDecimal.valueOf(100), BigDecimal.valueOf(260));
    }

    private boolean shouldAddSnack(PlanContext context, String preference) {
        String normalized = (nullToEmpty(preference) + " " + nullToEmpty(context.goalType())).toLowerCase(Locale.ROOT);
        return zeroSafe(context.targetCalories()).compareTo(BigDecimal.valueOf(1850)) >= 0
                || containsAny(normalized, List.of("加餐", "训练", "健身", "增肌", "运动"));
    }

    private String buildPlanTitle(PlanContext context, LocalDate planDate, boolean aiMode) {
        String prefix = resolveGoalLabel(context.goalType());
        String suffix = aiMode ? "智能饮食方案" : "执行型饮食方案";
        return planDate.getMonthValue() + "月" + planDate.getDayOfMonth() + "日" + prefix + suffix;
    }

    private String buildPlanNotes(PlanContext context, String preference, boolean aiMode) {
        List<String> parts = new ArrayList<>();
        parts.add(aiMode ? "按用户目标与知识检索结果生成。" : "按本地规则兜底生成，保证三餐完整可执行。");
        if (StringUtils.hasText(context.dietaryPreference())) {
            parts.add("已兼顾饮食偏好：" + context.dietaryPreference().trim());
        }
        if (StringUtils.hasText(context.allergies())) {
            parts.add("已避开过敏/不耐受提示：" + context.allergies().trim());
        }
        if (StringUtils.hasText(preference)) {
            parts.add("额外要求：" + preference.trim());
        }
        return String.join(" ", parts);
    }

    private String buildPlanSummary(PlanContext context, List<PlannedItem> items, boolean aiMode) {
        int snackCount = (int) items.stream().filter(item -> "SNACK".equals(item.mealType())).count();
        String style = "FAT_LOSS".equalsIgnoreCase(context.goalType()) ? "高蛋白、主食分层控制"
                : "MUSCLE_GAIN".equalsIgnoreCase(context.goalType()) ? "保证蛋白和总能量"
                : "三餐均衡、结构稳定";
        return "这份" + (aiMode ? "智能生成" : "规则兜底") + "方案围绕“" + resolveGoalLabel(context.goalType())
                + "”目标，采用" + style + "的思路，覆盖早餐、午餐、晚餐"
                + (snackCount > 0 ? "并补充 1 次加餐" : "") + "。";
    }

    private List<String> buildPlanTips(PlanContext context,
                                       String preference,
                                       List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        List<String> tips = new ArrayList<>();
        if ("FAT_LOSS".equalsIgnoreCase(context.goalType())) {
            tips.add("先吃蛋白和蔬菜，再吃主食，更容易稳住总量。");
        } else if ("MUSCLE_GAIN".equalsIgnoreCase(context.goalType())) {
            tips.add("训练前后优先保证蛋白和主食，不要把总量吃得太低。");
        } else {
            tips.add("三餐时间尽量固定，避免晚上集中补偿式进食。");
        }
        tips.add("每餐尽量保留一种优质蛋白，计划执行会更稳。");
        if (StringUtils.hasText(preference)) {
            tips.add("今天的偏好已纳入规划，执行时优先按计划主框架走。");
        }
        if (!knowledgeHits.isEmpty()) {
            tips.add(knowledgeHits.get(0).summary());
        }
        return uniqueStrings(tips, 3);
    }

    private List<String> buildWarnings(PlanContext context) {
        List<String> warnings = new ArrayList<>();
        if (StringUtils.hasText(context.allergies())) {
            warnings.add("执行前仍需再次确认是否包含你的过敏或不耐受食材：" + context.allergies().trim());
        }
        if (StringUtils.hasText(context.medicalNotes())) {
            warnings.add("如果存在明确疾病或医生特殊要求，请以专业医疗意见为准。");
        }
        return warnings;
    }

    private List<String> mergeWarnings(List<String> seedWarnings,
                                       PlanContext context,
                                       BigDecimal calorieGap,
                                       BigDecimal proteinGap) {
        List<String> warnings = new ArrayList<>(seedWarnings == null ? List.of() : seedWarnings);
        if (calorieGap.abs().compareTo(BigDecimal.valueOf(250)) > 0) {
            warnings.add("本次计划与目标热量仍有一定偏差，建议结合实际饱腹感继续微调。");
        }
        if (proteinGap.compareTo(BigDecimal.valueOf(-15)) < 0) {
            warnings.add("当前计划蛋白略低于目标，如有训练安排可适当加一份蛋白来源。");
        }
        warnings.addAll(buildWarnings(context));
        return uniqueStrings(warnings, 3);
    }

    private String buildKnowledgeQuery(PlanContext context, String preference) {
        return String.join(" ",
                List.of(
                        "一日三餐",
                        resolveGoalLabel(context.goalType()),
                        nullToEmpty(context.dietaryPreference()),
                        nullToEmpty(context.allergies()),
                        nullToEmpty(preference)
                )).trim();
    }

    private String buildWeekSummary(PlanContext context, List<GeneratedMealPlanResponse> days) {
        if (days.isEmpty()) {
            return "本周暂无可展示的饮食计划。";
        }
        BigDecimal avgCalories = days.stream()
                .map(GeneratedMealPlanResponse::getTotalCalories)
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(days.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgProtein = days.stream()
                .map(GeneratedMealPlanResponse::getTotalProtein)
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(days.size()), 2, RoundingMode.HALF_UP);
        return "本周计划从周一到周日覆盖 7 天，围绕“" + resolveGoalLabel(context.goalType())
                + "”目标做了多样化安排，平均每日约 " + formatDecimal(avgCalories) + " 千卡、"
                + formatDecimal(avgProtein) + " 克蛋白。";
    }

    private String resolveWeekGenerationMode(Set<String> modes) {
        if (modes == null || modes.isEmpty()) {
            return MODE_RULE_BASED;
        }
        if (modes.size() == 1) {
            return modes.iterator().next();
        }
        return MODE_MIXED;
    }

    private void rememberFoods(List<String> recentFoodNames, GeneratedMealPlanResponse response) {
        if (response.getItems() == null) {
            return;
        }
        for (MealPlanItemResponse item : response.getItems()) {
            if (StringUtils.hasText(item.getFoodName())) {
                recentFoodNames.add(item.getFoodName().trim());
            }
        }
        while (recentFoodNames.size() > 18) {
            recentFoodNames.remove(0);
        }
    }

    private List<String> toReferenceList(List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (NutritionKnowledgeBaseService.KnowledgeHit hit : knowledgeHits) {
            String label = hit.title();
            if (StringUtils.hasText(hit.section())) {
                label += " - " + hit.section();
            }
            references.add(label);
        }
        return uniqueStrings(references, 4);
    }

    private List<PlannedItem> reIndex(List<PlannedItem> items) {
        List<PlannedItem> indexed = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PlannedItem item = items.get(i);
            indexed.add(new PlannedItem(
                    item.foodId(),
                    item.foodName(),
                    item.mealType(),
                    item.quantity(),
                    item.note(),
                    i
            ));
        }
        return indexed;
    }

    private List<String> readStringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node.get(field)) {
            String text = element == null ? null : element.asText(null);
            if (StringUtils.hasText(text)) {
                values.add(text.trim());
            }
        }
        return values;
    }

    @SafeVarargs
    private final String joinFoodNames(List<FoodNutritionSnapshot>... groups) {
        List<String> names = new ArrayList<>();
        for (List<FoodNutritionSnapshot> group : groups) {
            if (group == null) {
                continue;
            }
            for (FoodNutritionSnapshot food : group) {
                if (food != null && StringUtils.hasText(food.getName())) {
                    names.add(food.getName().trim());
                }
            }
        }
        return String.join("、", uniqueStrings(names, 20));
    }

    private boolean containsFoodName(List<String> recentFoodNames, String foodName) {
        if (!StringUtils.hasText(foodName) || recentFoodNames == null || recentFoodNames.isEmpty()) {
            return false;
        }
        String normalized = foodName.trim().toLowerCase(Locale.ROOT);
        return recentFoodNames.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    private int compareMealType(String left, String right) {
        return Integer.compare(mealTypeOrder(left), mealTypeOrder(right));
    }

    private int mealTypeOrder(String mealType) {
        return switch (resolveMealType(mealType)) {
            case "BREAKFAST" -> 0;
            case "LUNCH" -> 1;
            case "DINNER" -> 2;
            case "SNACK" -> 3;
            default -> 9;
        };
    }

    private BigDecimal clampScale(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal safe = zeroSafe(value);
        if (safe.compareTo(min) < 0) {
            return min.setScale(2, RoundingMode.HALF_UP);
        }
        if (safe.compareTo(max) > 0) {
            return max.setScale(2, RoundingMode.HALF_UP);
        }
        return safe.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        return source.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private List<String> uniqueStrings(List<String> source, int limit) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : source) {
            if (StringUtils.hasText(value)) {
                unique.add(value.trim());
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique);
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

    private record CandidateFoodPool(
            List<FoodNutritionSnapshot> breakfastStaples,
            List<FoodNutritionSnapshot> breakfastProteins,
            List<FoodNutritionSnapshot> proteins,
            List<FoodNutritionSnapshot> carbs,
            List<FoodNutritionSnapshot> vegetables,
            List<FoodNutritionSnapshot> fruits,
            List<FoodNutritionSnapshot> snacks
    ) {
        private List<FoodNutritionSnapshot> allFoods() {
            LinkedHashMap<Long, FoodNutritionSnapshot> unique = new LinkedHashMap<>();
            for (FoodNutritionSnapshot food : concat(breakfastStaples, breakfastProteins, proteins, carbs, vegetables, fruits, snacks)) {
                if (food != null && food.getId() != null) {
                    unique.putIfAbsent(food.getId(), food);
                }
            }
            return new ArrayList<>(unique.values());
        }

        @SafeVarargs
        private static List<FoodNutritionSnapshot> concat(List<FoodNutritionSnapshot>... groups) {
            List<FoodNutritionSnapshot> values = new ArrayList<>();
            for (List<FoodNutritionSnapshot> group : groups) {
                if (group != null) {
                    values.addAll(group);
                }
            }
            return values;
        }
    }

    private record PlannedItem(
            Long foodId,
            String foodName,
            String mealType,
            BigDecimal quantity,
            String note,
            int sortOrder
    ) {
    }

    private record PlanningDraft(
            String generationMode,
            String title,
            String notes,
            String summary,
            List<String> tips,
            List<String> warnings,
            List<String> references,
            List<PlannedItem> items
    ) {
    }
}
