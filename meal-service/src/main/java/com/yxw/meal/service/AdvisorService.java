package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.config.RagProperties;
import com.yxw.meal.client.UserProfileClient;
import com.yxw.meal.client.dto.HealthGoalSnapshot;
import com.yxw.meal.client.dto.HealthProfileSnapshot;
import com.yxw.meal.client.dto.ProfileOverviewSnapshot;
import com.yxw.meal.client.dto.WeightLogSnapshot;
import com.yxw.meal.dto.AdvisorMessageRequest;
import com.yxw.meal.dto.AdvisorMessageResponse;
import com.yxw.meal.dto.AdvisorReferenceResponse;
import com.yxw.meal.dto.AgentExecutionDetailResponse;
import com.yxw.meal.entity.AdvisorMessage;
import com.yxw.meal.entity.MealPlan;
import com.yxw.meal.entity.MealRecord;
import com.yxw.meal.mapper.AdvisorMessageMapper;
import com.yxw.meal.mapper.MealPlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);
    private static final TypeReference<List<AdvisorReferenceResponse>> REFERENCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final BigDecimal DEFAULT_TARGET_CALORIES = BigDecimal.valueOf(2000);
    private static final BigDecimal DEFAULT_TARGET_PROTEIN = BigDecimal.valueOf(90);
    private static final int KNOWLEDGE_LIMIT = 3;
    private static final int HISTORY_LIMIT = 6;
    private static final int WINDOW_DAYS = 7;
    private static final int MAX_MESSAGE_LENGTH = 1800;

    private final AdvisorMessageMapper advisorMessageMapper;
    private final MealRecordService mealRecordService;
    private final MealPlanMapper mealPlanMapper;
    private final UserProfileClient userProfileClient;
    private final NutritionKnowledgeBaseService nutritionKnowledgeBaseService;
    private final MilvusKnowledgeStoreService milvusKnowledgeStoreService;
    private final FoodGraphContextService foodGraphContextService;
    private final AgentExecutionLogService agentExecutionLogService;
    private final QwenModelStudioService qwenModelStudioService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public AdvisorService(AdvisorMessageMapper advisorMessageMapper,
                          MealRecordService mealRecordService,
                          MealPlanMapper mealPlanMapper,
                          UserProfileClient userProfileClient,
                          NutritionKnowledgeBaseService nutritionKnowledgeBaseService,
                          MilvusKnowledgeStoreService milvusKnowledgeStoreService,
                          FoodGraphContextService foodGraphContextService,
                          AgentExecutionLogService agentExecutionLogService,
                          QwenModelStudioService qwenModelStudioService,
                          RagProperties ragProperties,
                          ObjectMapper objectMapper) {
        this.advisorMessageMapper = advisorMessageMapper;
        this.mealRecordService = mealRecordService;
        this.mealPlanMapper = mealPlanMapper;
        this.userProfileClient = userProfileClient;
        this.nutritionKnowledgeBaseService = nutritionKnowledgeBaseService;
        this.milvusKnowledgeStoreService = milvusKnowledgeStoreService;
        this.foodGraphContextService = foodGraphContextService;
        this.agentExecutionLogService = agentExecutionLogService;
        this.qwenModelStudioService = qwenModelStudioService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    public List<AdvisorMessageResponse> listMessages(Long userId) {
        ensureWelcomeMessage(userId);
        return advisorMessageMapper.selectList(new LambdaQueryWrapper<AdvisorMessage>()
                        .eq(AdvisorMessage::getUserId, userId)
                        .orderByAsc(AdvisorMessage::getCreatedAt)
                        .orderByAsc(AdvisorMessage::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AdvisorMessageResponse sendMessage(Long userId, AdvisorMessageRequest request) {
        ensureWelcomeMessage(userId);

        String content = request.getContent().trim();

        AdvisorMessage userMessage = new AdvisorMessage();
        userMessage.setUserId(userId);
        userMessage.setRole("USER");
        userMessage.setContent(content);
        advisorMessageMapper.insert(userMessage);

        GeneratedReply generatedReply = generateReply(userId, content, listRecentMessages(userId, HISTORY_LIMIT));

        AdvisorMessage assistantMessage = new AdvisorMessage();
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(generatedReply.content());
        assistantMessage.setReferencesJson(writeReferences(generatedReply.references()));
        advisorMessageMapper.insert(assistantMessage);
        AdvisorMessageResponse response = toResponse(assistantMessage);
        response.setExecutionDetail(generatedReply.executionDetail());
        return response;
    }

    private void ensureWelcomeMessage(Long userId) {
        Long count = advisorMessageMapper.selectCount(new LambdaQueryWrapper<AdvisorMessage>()
                .eq(AdvisorMessage::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }

        AdvisorMessage welcome = new AdvisorMessage();
        welcome.setUserId(userId);
        welcome.setRole("ASSISTANT");
        welcome.setContent("你好，我是你的专属营养顾问。现在我会结合你的饮食记录、健康目标、营养知识检索结果来回答问题；当外部大模型可用时，也会生成更自然的个性化建议。你可以直接问我减脂、增肌、外卖选择、早餐搭配或今天的摄入情况。");
        advisorMessageMapper.insert(welcome);
    }

    private GeneratedReply generateReply(Long userId, String question, List<AdvisorMessage> recentMessages) {
        AgentExecutionLogService.AgentExecutionTracker tracker = agentExecutionLogService.start(
                "ADVISOR_CHAT",
                userId,
                limitForPrompt(question, 240));
        try {
            long perceptionStart = System.currentTimeMillis();
            AdvisorContext context = buildContext(userId);
            String retrievalQuery = buildRetrievalQuery(question, recentMessages, context);
            List<String> anchorFoods = extractAdvisorAnchorFoods(question, recentMessages);
            tracker.step(
                    "PerceptionAgent",
                    "PERCEPTION",
                    "SUCCESS",
                    limitForPrompt(question, 240),
                    "query=" + limitForPrompt(retrievalQuery, 180) + ", anchors=" + String.join(", ", anchorFoods),
                    buildContextSummary(context),
                    System.currentTimeMillis() - perceptionStart);

            long graphStart = System.currentTimeMillis();
            FoodGraphContextService.GraphContext graphContext = foodGraphContextService.buildContext(
                    retrievalQuery,
                    anchorFoods,
                    Math.max(4, resolveKnowledgeLimit()));
            tracker.step(
                    "GraphRetrievalAgent",
                    "GRAPH_RETRIEVAL",
                    "SUCCESS",
                    limitForPrompt(retrievalQuery, 180),
                    limitForPrompt(buildGraphSummary(graphContext), 400),
                    graphContext == null ? null : String.join(" | ", graphContext.planReferences()),
                    System.currentTimeMillis() - graphStart);

            long knowledgeStart = System.currentTimeMillis();
            List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits = searchKnowledge(retrievalQuery);
            tracker.step(
                    "KnowledgeRetrievalAgent",
                    "DOCUMENT_RETRIEVAL",
                    "SUCCESS",
                    limitForPrompt(retrievalQuery, 180),
                    "hits=" + knowledgeHits.size(),
                    buildKnowledgeTitles(knowledgeHits),
                    System.currentTimeMillis() - knowledgeStart);

            List<AdvisorReferenceResponse> references = mergeReferences(graphContext, knowledgeHits);

            long generationStart = System.currentTimeMillis();
            String content = generateWithQwen(question, recentMessages, context, graphContext, knowledgeHits);
            String generationMode = "AI_GRAPH_RAG";
            if (!StringUtils.hasText(content)) {
                content = composeReply(question, context, graphContext, knowledgeHits);
                generationMode = "RULE_GRAPH_RAG";
            }
            tracker.step(
                    "NutritionAdvisorAgent",
                    "RESPONSE_GENERATION",
                    "SUCCESS",
                    limitForPrompt(question, 240),
                    limitForPrompt(content, 400),
                    "references=" + references.size(),
                    System.currentTimeMillis() - generationStart);
            tracker.complete("SUCCESS", limitForPrompt(content, 240), generationMode);
            return new GeneratedReply(
                    limitMessage(content),
                    references,
                    agentExecutionLogService.getExecutionDetail(tracker.getExecutionId()));
        } catch (RuntimeException exception) {
            tracker.fail(exception);
            throw exception;
        }
    }

    private AdvisorContext buildContext(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(WINDOW_DAYS - 1L);
        List<MealRecord> recentRecords = mealRecordService.lambdaQuery()
                .eq(MealRecord::getUserId, userId)
                .between(MealRecord::getRecordDate, startDate, today)
                .orderByAsc(MealRecord::getRecordDate)
                .list();

        MealRecord todayRecord = recentRecords.stream()
                .filter(item -> today.equals(item.getRecordDate()))
                .findFirst()
                .orElse(null);
        ProfileOverviewSnapshot profileOverview = fetchProfileOverview();
        HealthGoalSnapshot goal = profileOverview == null ? null : profileOverview.getHealthGoal();
        HealthProfileSnapshot healthProfile = profileOverview == null ? null : profileOverview.getHealthProfile();

        BigDecimal latestWeight = profileOverview == null ? null : profileOverview.getLatestWeightKg();
        BigDecimal weightDelta = calculateWeightDelta(profileOverview == null ? null : profileOverview.getRecentWeightLogs());
        String displayName = firstNonBlank(
                profileOverview == null ? null : profileOverview.getNickname(),
                profileOverview == null ? null : profileOverview.getUsername(),
                SecurityContextUtils.currentUsername().orElse(null),
                "你");

        MealPlan todayPlan = mealPlanMapper.selectOne(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getUserId, userId)
                .eq(MealPlan::getPlanDate, today));

        return new AdvisorContext(
                displayName,
                goal == null ? null : goal.getGoalType(),
                firstNonNull(goal == null ? null : goal.getTargetCalories(), DEFAULT_TARGET_CALORIES),
                firstNonNull(goal == null ? null : goal.getTargetProtein(), DEFAULT_TARGET_PROTEIN),
                average(recentRecords.stream().map(MealRecord::getTotalCalories).toList(), WINDOW_DAYS),
                average(recentRecords.stream().map(MealRecord::getTotalProtein).toList(), WINDOW_DAYS),
                zeroSafe(todayRecord == null ? null : todayRecord.getTotalCalories()),
                zeroSafe(todayRecord == null ? null : todayRecord.getTotalProtein()),
                recentRecords.size(),
                latestWeight,
                weightDelta,
                healthProfile == null ? null : healthProfile.getDietaryPreference(),
                healthProfile == null ? null : healthProfile.getAllergies(),
                healthProfile == null ? null : healthProfile.getMedicalNotes(),
                todayPlan
        );
    }

    private ProfileOverviewSnapshot fetchProfileOverview() {
        try {
            return userProfileClient.getOverview();
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Advisor fallback: failed to load profile overview", exception);
            return null;
        }
    }

    private String composeReply(String question,
                                AdvisorContext context,
                                FoodGraphContextService.GraphContext graphContext,
                                List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        List<String> lines = new ArrayList<>();
        lines.add(context.displayName() + "，我把你的最近记录、目标和营养知识库内容一起看了一下。");
        lines.add("");
        lines.add("你的当前情况：");
        lines.add("1. " + buildRecentSummary(context));
        lines.add("2. " + buildTodaySummary(context));

        String goalSummary = buildGoalSummary(context);
        if (StringUtils.hasText(goalSummary)) {
            lines.add("3. " + goalSummary);
        }

        lines.add("");
        lines.add("更适合你的做法：");
        List<String> adviceItems = buildAdviceItems(question, context, graphContext, knowledgeHits);
        for (int index = 0; index < adviceItems.size(); index++) {
            lines.add((index + 1) + ". " + adviceItems.get(index));
        }

        String safetyReminder = buildSafetyReminder(context);
        if (StringUtils.hasText(safetyReminder)) {
            lines.add("");
            lines.add(safetyReminder);
        }

        lines.add("");
        lines.add("如果你愿意，我可以继续按早餐、午餐、晚餐、加餐或外卖场景帮你细化到一整天。");
        return String.join("\n", lines);
    }

    private List<String> buildAdviceItems(String question,
                                          AdvisorContext context,
                                          FoodGraphContextService.GraphContext graphContext,
                                          List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        List<String> items = new ArrayList<>();
        items.add(buildPersonalizedAction(question, context));
        if (graphContext != null && graphContext.actionHints() != null) {
            items.addAll(graphContext.actionHints());
        }

        if (context.todayPlan() != null
                && context.todayPlan().getTotalCalories() != null
                && context.todayPlan().getTotalCalories().compareTo(BigDecimal.ZERO) > 0
                && looksLikeDailyPlanningQuestion(question)) {
            items.add(String.format(
                    Locale.ROOT,
                    "你今天已经有一份计划餐，计划总量大约 %s 千卡、蛋白质 %s 克，执行时优先把计划里的蛋白质和蔬菜吃到位，再根据饥饿程度调整主食。",
                    formatDecimal(context.todayPlan().getTotalCalories()),
                    formatDecimal(context.todayPlan().getTotalProtein())));
        }

        Set<String> deduplicated = new LinkedHashSet<>(items);
        for (NutritionKnowledgeBaseService.KnowledgeHit knowledgeHit : knowledgeHits) {
            if (deduplicated.size() >= 3) {
                break;
            }
            deduplicated.add(knowledgeHit.summary());
        }

        if (deduplicated.size() < 3) {
            deduplicated.add(buildFallbackAdvice(context));
        }

        return deduplicated.stream().limit(3).toList();
    }

    private String buildRecentSummary(AdvisorContext context) {
        if (context.recordedDays() == 0) {
            return "最近 7 天还没有形成完整饮食记录，先连续记录 3 天以上，我就能把建议校准到更贴近你的真实摄入。";
        }
        return String.format(
                Locale.ROOT,
                "最近 7 天平均每天摄入 %s 千卡、蛋白质 %s 克，记录了 %d/7 天。",
                formatDecimal(context.averageCalories()),
                formatDecimal(context.averageProtein()),
                context.recordedDays());
    }

    private String buildTodaySummary(AdvisorContext context) {
        BigDecimal remainingCalories = remaining(context.targetCalories(), context.todayCalories());
        BigDecimal remainingProtein = remaining(context.targetProtein(), context.todayProtein());
        if (context.todayCalories().compareTo(BigDecimal.ZERO) <= 0 && context.todayProtein().compareTo(BigDecimal.ZERO) <= 0) {
            return String.format(
                    Locale.ROOT,
                    "今天还没有新增饮食记录，你当前可以先按大约 %s 千卡、蛋白质 %s 克的目标去安排。",
                    formatDecimal(context.targetCalories()),
                    formatDecimal(context.targetProtein()));
        }
        return String.format(
                Locale.ROOT,
                "今天已记录 %s 千卡、蛋白质 %s 克，距离当前目标还差大约 %s 千卡和 %s 克蛋白质。",
                formatDecimal(context.todayCalories()),
                formatDecimal(context.todayProtein()),
                formatDecimal(remainingCalories),
                formatDecimal(remainingProtein));
    }

    private String buildGoalSummary(AdvisorContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前目标是")
                .append(resolveGoalLabel(context.goalType()))
                .append("，参考目标约 ")
                .append(formatDecimal(context.targetCalories()))
                .append(" 千卡 / ")
                .append(formatDecimal(context.targetProtein()))
                .append(" 克蛋白质");

        if (context.latestWeightKg() != null) {
            builder.append("；最近体重 ")
                    .append(formatDecimal(context.latestWeightKg()))
                    .append(" kg");
            if (context.weightDeltaKg() != null && context.weightDeltaKg().compareTo(BigDecimal.ZERO) != 0) {
                builder.append("，相对最早一条记录变化 ")
                        .append(formatSignedDecimal(context.weightDeltaKg()))
                        .append(" kg");
            }
        }

        if (StringUtils.hasText(context.dietaryPreference())) {
            builder.append("；饮食偏好：").append(context.dietaryPreference());
        }
        return builder.toString();
    }

    private String buildPersonalizedAction(String question, AdvisorContext context) {
        String normalized = question.toLowerCase(Locale.ROOT);
        BigDecimal remainingCalories = remaining(context.targetCalories(), context.todayCalories());
        BigDecimal remainingProtein = remaining(context.targetProtein(), context.todayProtein());

        if (looksLikeMealQuestion(normalized, "早餐", "早上")) {
            return "早餐优先用“主食 + 优质蛋白 + 水果”起步，比如燕麦加牛奶、鸡蛋和一份水果；如果你今天蛋白质还差得多，早餐就别只吃面包和咖啡。";
        }
        if (looksLikeMealQuestion(normalized, "午餐", "中午")) {
            return "午餐更适合把主食和蛋白质吃扎实一些，先保证一份明确蛋白主菜，再配一份蔬菜，主食按目标留 1/3 到 1/2 给晚餐。";
        }
        if (looksLikeMealQuestion(normalized, "晚餐")) {
            return "晚餐先把蛋白质和蔬菜吃到位，再按饥饿程度决定主食量；如果你今天热量已经接近目标，晚餐就尽量少油少酱。";
        }
        if (looksLikeMealQuestion(normalized, "外卖", "吃什么", "推荐")) {
            return "点外卖时先选蛋白主菜，再配蔬菜，最后再决定主食和饮料；如果你今天还差蛋白质，可以优先鸡胸肉、牛肉、鱼、豆腐和无糖酸奶这一类。";
        }
        if (normalized.contains("蛋白")) {
            return String.format(
                    Locale.ROOT,
                    "你今天蛋白质还差大约 %s 克时，更适合优先补鸡蛋、鸡胸肉、牛奶、酸奶、豆腐这类高蛋白食物，不要把蛋白质额度都留给高油高糖零食。",
                    formatDecimal(remainingProtein));
        }
        if (normalized.contains("热量") || normalized.contains("今天")) {
            if (remainingCalories.compareTo(BigDecimal.ZERO) <= 0) {
                return "今天热量已经接近或超过目标，后面的加餐尽量控制在高蛋白、低油低糖的范围内，别再用奶茶和炸物补情绪。";
            }
            return String.format(
                    Locale.ROOT,
                    "今天还差大约 %s 千卡、%s 克蛋白质，接下来更适合用清淡正餐或高蛋白加餐补足，而不是把剩余额度一次性留给高糖饮料和重油外卖。",
                    formatDecimal(remainingCalories),
                    formatDecimal(remainingProtein));
        }
        if ("FAT_LOSS".equalsIgnoreCase(context.goalType())) {
            if (context.averageCalories().compareTo(context.targetCalories().add(BigDecimal.valueOf(150))) > 0) {
                return "你当前减脂的核心不是继续极端节食，而是先把平均热量拉回目标附近，比如从晚餐主食减半碗、饮料换无糖、零食换高蛋白加餐开始。";
            }
            if (context.averageCalories().compareTo(context.targetCalories().subtract(BigDecimal.valueOf(300))) < 0) {
                return "你最近平均热量已经偏低，别再继续猛砍总量，重点改成把蛋白质和蔬菜补足，避免白天吃太少、晚上补偿性进食。";
            }
            return "你目前的减脂节奏还算可控，下一步重点是把蛋白质稳定拉到目标附近，并且尽量把每天的主食量控制得更平稳。";
        }
        if ("MUSCLE_GAIN".equalsIgnoreCase(context.goalType())) {
            if (context.averageProtein().compareTo(context.targetProtein()) < 0) {
                return "你当前增肌的短板更像是蛋白质没吃够，先把每餐固定一个蛋白来源，再把一部分主食放在训练前后，比盲目加零食更稳。";
            }
            return "你现在更适合把训练前后两餐的主食和蛋白固定下来，优先保证恢复和总量，不要只靠夜宵或零散加餐堆热量。";
        }
        return "先把每一餐都做成“有蛋白、主食量稳定、蔬菜跟上”的结构，连续执行几天后，我就能根据你的记录给出更精细的调整。";
    }

    private String buildFallbackAdvice(AdvisorContext context) {
        if ("MUSCLE_GAIN".equalsIgnoreCase(context.goalType())) {
            return "增肌阶段更看重总量和恢复，三餐都要有明确蛋白来源，训练前后别把主食吃得太少。";
        }
        if ("FAT_LOSS".equalsIgnoreCase(context.goalType())) {
            return "减脂阶段最稳的做法是维持可持续的小缺口，同时把蛋白质和蔬菜顶上去，避免白天过度克制、晚上失控。";
        }
        return "均衡饮食最关键的是规律记录、稳定主食量和优先蛋白质，这样你的报表趋势才会越来越有参考价值。";
    }

    private String buildSafetyReminder(AdvisorContext context) {
        List<String> reminders = new ArrayList<>();
        if (StringUtils.hasText(context.allergies())) {
            reminders.add("避开过敏或不耐受食材：" + context.allergies());
        }
        if (StringUtils.hasText(context.dietaryPreference())) {
            reminders.add("尽量继续符合你的饮食偏好：" + context.dietaryPreference());
        }
        if (StringUtils.hasText(context.medicalNotes())) {
            reminders.add("你档案里还有健康备注：" + context.medicalNotes() + "，如果涉及疾病管理、用药或明显不适，最好同时和医生确认");
        }
        if (reminders.isEmpty()) {
            return null;
        }
        return "补充提醒：" + String.join("；", reminders) + "。";
    }

    private String buildRetrievalQuery(String question, List<AdvisorMessage> recentMessages, AdvisorContext context) {
        StringBuilder queryBuilder = new StringBuilder(question);
        if (looksLikeFollowUp(question)) {
            recentMessages.stream()
                    .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
                    .map(AdvisorMessage::getContent)
                    .filter(previous -> StringUtils.hasText(previous) && !Objects.equals(previous, question))
                    .findFirst()
                    .ifPresent(previous -> queryBuilder.append(' ').append(previous));
        }
        if (StringUtils.hasText(context.goalType())) {
            queryBuilder.append(' ').append(resolveGoalLabel(context.goalType()));
        }
        if (StringUtils.hasText(context.dietaryPreference())) {
            queryBuilder.append(' ').append(context.dietaryPreference());
        }
        if (StringUtils.hasText(context.allergies())) {
            queryBuilder.append(' ').append(context.allergies());
        }
        return queryBuilder.toString();
    }

    private List<String> extractAdvisorAnchorFoods(String question, List<AdvisorMessage> recentMessages) {
        LinkedHashSet<String> anchorFoods = new LinkedHashSet<>();
        collectFoodNames(anchorFoods, question);
        if (recentMessages != null) {
            recentMessages.stream()
                    .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
                    .map(AdvisorMessage::getContent)
                    .limit(2)
                    .forEach(content -> collectFoodNames(anchorFoods, content));
        }
        return new ArrayList<>(anchorFoods).stream().limit(6).toList();
    }

    private void collectFoodNames(Set<String> anchorFoods, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String normalized = text.trim();
        String[] candidates = {
                "鸡腿", "鸡胸肉", "鸡蛋", "牛肉", "三文鱼", "鱼", "虾", "豆腐", "燕麦", "燕麦片",
                "米饭", "全麦面包", "牛奶", "酸奶", "西兰花", "黄瓜", "番茄", "苹果", "香蕉"
        };
        for (String candidate : candidates) {
            if (normalized.contains(candidate)) {
                anchorFoods.add(candidate);
            }
        }
    }

    private List<NutritionKnowledgeBaseService.KnowledgeHit> searchKnowledge(String retrievalQuery) {
        int limit = resolveKnowledgeLimit();
        List<NutritionKnowledgeBaseService.KnowledgeHit> milvusHits =
                milvusKnowledgeStoreService.search(retrievalQuery, limit);
        if (!milvusHits.isEmpty()) {
            return milvusHits;
        }
        return nutritionKnowledgeBaseService.search(retrievalQuery, limit);
    }

    private int resolveKnowledgeLimit() {
        return ragProperties.getTopK() > 0 ? ragProperties.getTopK() : KNOWLEDGE_LIMIT;
    }

    private String generateWithQwen(String question,
                                    List<AdvisorMessage> recentMessages,
                                    AdvisorContext context,
                                    FoodGraphContextService.GraphContext graphContext,
                                    List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        if (!ragProperties.isEnabled() || !qwenModelStudioService.isReady()) {
            return null;
        }

        try {
            return qwenModelStudioService.generateAdvisorAnswer(
                    buildSystemPrompt(),
                    buildUserPrompt(question, recentMessages, context, graphContext, knowledgeHits)
            );
        } catch (RuntimeException exception) {
            log.warn("Advisor fallback: failed to generate answer with Qwen", exception);
            return null;
        }
    }

    private String buildSystemPrompt() {
        return """
                你是 NutriMind 的营养顾问助手，需要基于用户的真实饮食记录、健康目标和检索到的营养知识提供个性化建议。
                请遵守以下规则：
                1. 优先依据给定的用户上下文和检索知识回答，不要虚构用户数据。
                2. 当检索知识不足时，可以给出保守、通用的营养建议，但要避免说得过于绝对。
                3. 回答使用中文，语气专业、自然、鼓励式，尽量直接给出可执行建议。
                4. 回答尽量控制在 3 到 6 句话或 3 条建议内，不要长篇大论。
                5. 不要输出“作为 AI”之类表述，也不要编造引用编号。
                6. 如果涉及疾病管理、药物、严重不适或过敏风险，提醒用户结合医生建议。
                """;
    }

    private String buildUserPrompt(String question,
                                   List<AdvisorMessage> recentMessages,
                                   AdvisorContext context,
                                   FoodGraphContextService.GraphContext graphContext,
                                   List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【用户当前问题】\n")
                .append(limitForPrompt(question, 240))
                .append("\n\n【最近对话】\n")
                .append(buildHistorySummary(recentMessages))
                .append("\n\n【用户画像与记录】\n")
                .append(buildContextSummary(context))
                .append("\n\n[GRAPH_CONTEXT]\n")
                .append(buildGraphSummary(graphContext))
                .append("\n\n【检索到的营养知识】\n")
                .append(buildKnowledgeSummary(knowledgeHits))
                .append("\n\n请给出一段最终回复，优先结合用户的实际记录和检索知识，先判断问题，再给 2 到 3 条最值得执行的建议；如果有必要，最后补一句风险提醒。");
        return prompt.toString();
    }

    private String buildHistorySummary(List<AdvisorMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "暂无历史对话。";
        }

        return recentMessages.stream()
                .filter(message -> StringUtils.hasText(message.getContent()))
                .sorted(Comparator
                        .comparing(AdvisorMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AdvisorMessage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(message -> ("USER".equalsIgnoreCase(message.getRole()) ? "用户： " : "顾问： ")
                        + limitForPrompt(message.getContent(), 160))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("暂无历史对话。");
    }

    private String buildContextSummary(AdvisorContext context) {
        List<String> lines = new ArrayList<>();
        lines.add("用户称呼： " + context.displayName());
        lines.add("当前目标： " + resolveGoalLabel(context.goalType()));
        lines.add("目标摄入： " + formatDecimal(context.targetCalories()) + " 千卡，蛋白质 "
                + formatDecimal(context.targetProtein()) + " 克");
        lines.add("最近 7 天平均： " + formatDecimal(context.averageCalories()) + " 千卡，蛋白质 "
                + formatDecimal(context.averageProtein()) + " 克，记录天数 " + context.recordedDays() + "/" + WINDOW_DAYS);
        lines.add("今天已记录： " + formatDecimal(context.todayCalories()) + " 千卡，蛋白质 "
                + formatDecimal(context.todayProtein()) + " 克");

        if (context.latestWeightKg() != null) {
            String weightLine = "最近体重： " + formatDecimal(context.latestWeightKg()) + " kg";
            if (context.weightDeltaKg() != null) {
                weightLine = weightLine + "，相对最早记录变化 " + formatSignedDecimal(context.weightDeltaKg()) + " kg";
            }
            lines.add(weightLine);
        }
        if (StringUtils.hasText(context.dietaryPreference())) {
            lines.add("饮食偏好： " + context.dietaryPreference());
        }
        if (StringUtils.hasText(context.allergies())) {
            lines.add("过敏/不耐受： " + context.allergies());
        }
        if (StringUtils.hasText(context.medicalNotes())) {
            lines.add("健康备注： " + limitForPrompt(context.medicalNotes(), 180));
        }
        if (context.todayPlan() != null
                && context.todayPlan().getTotalCalories() != null
                && context.todayPlan().getTotalProtein() != null) {
            lines.add("今日计划餐： " + formatDecimal(context.todayPlan().getTotalCalories()) + " 千卡，蛋白质 "
                    + formatDecimal(context.todayPlan().getTotalProtein()) + " 克");
        }
        return String.join("\n", lines);
    }

    private String buildKnowledgeSummary(List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return "暂无检索结果，可结合用户画像给出保守建议。";
        }

        List<String> lines = new ArrayList<>();
        for (int index = 0; index < knowledgeHits.size(); index++) {
            NutritionKnowledgeBaseService.KnowledgeHit hit = knowledgeHits.get(index);
            lines.add((index + 1) + ". "
                    + hit.title()
                    + " / "
                    + hit.section()
                    + "："
                    + limitForPrompt(firstNonBlank(hit.summary(), hit.excerpt(), "无摘要"), 220));
        }
        return String.join("\n", lines);
    }

    private String buildGraphSummary(FoodGraphContextService.GraphContext graphContext) {
        if (graphContext == null || !StringUtils.hasText(graphContext.promptSummary())) {
            return "No graph relation matched.";
        }
        return graphContext.promptSummary();
    }

    private String buildKnowledgeTitles(List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return null;
        }
        List<String> titles = new ArrayList<>();
        for (NutritionKnowledgeBaseService.KnowledgeHit knowledgeHit : knowledgeHits) {
            titles.add(firstNonBlank(knowledgeHit.title(), knowledgeHit.sourceName(), "knowledge-hit"));
        }
        return String.join(" | ", titles);
    }

    private List<AdvisorReferenceResponse> mergeReferences(FoodGraphContextService.GraphContext graphContext,
                                                           List<NutritionKnowledgeBaseService.KnowledgeHit> knowledgeHits) {
        List<AdvisorReferenceResponse> merged = new ArrayList<>();
        Set<String> fingerprints = new LinkedHashSet<>();
        if (graphContext != null && graphContext.advisorReferences() != null) {
            for (AdvisorReferenceResponse reference : graphContext.advisorReferences()) {
                appendReference(merged, fingerprints, reference);
            }
        }
        if (knowledgeHits != null) {
            for (NutritionKnowledgeBaseService.KnowledgeHit knowledgeHit : knowledgeHits) {
                appendReference(merged, fingerprints, toReference(knowledgeHit));
            }
        }
        return merged.stream().limit(6).toList();
    }

    private void appendReference(List<AdvisorReferenceResponse> merged,
                                 Set<String> fingerprints,
                                 AdvisorReferenceResponse reference) {
        if (reference == null) {
            return;
        }
        String fingerprint = String.join("|",
                safeText(reference.getTitle()),
                safeText(reference.getSection()),
                safeText(reference.getSourceName()));
        if (fingerprints.add(fingerprint)) {
            merged.add(reference);
        }
    }

    private String limitForPrompt(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private List<AdvisorMessage> listRecentMessages(Long userId, int limit) {
        return advisorMessageMapper.selectList(new LambdaQueryWrapper<AdvisorMessage>()
                .eq(AdvisorMessage::getUserId, userId)
                .orderByDesc(AdvisorMessage::getCreatedAt)
                .orderByDesc(AdvisorMessage::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    private AdvisorReferenceResponse toReference(NutritionKnowledgeBaseService.KnowledgeHit knowledgeHit) {
        return AdvisorReferenceResponse.builder()
                .title(knowledgeHit.title())
                .section(knowledgeHit.section())
                .excerpt(knowledgeHit.excerpt())
                .authority(knowledgeHit.authority())
                .sourceName(knowledgeHit.sourceName())
                .sourceUrl(knowledgeHit.sourceUrl())
                .build();
    }

    private String writeReferences(List<AdvisorReferenceResponse> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize advisor references", exception);
            return null;
        }
    }

    private List<AdvisorReferenceResponse> readReferences(String referencesJson) {
        if (!StringUtils.hasText(referencesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(referencesJson, REFERENCE_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to deserialize advisor references", exception);
            return List.of();
        }
    }

    private BigDecimal calculateWeightDelta(List<WeightLogSnapshot> logs) {
        if (logs == null || logs.size() < 2) {
            return null;
        }
        BigDecimal latest = logs.get(0).getWeightKg();
        BigDecimal earliest = logs.get(logs.size() - 1).getWeightKg();
        if (latest == null || earliest == null) {
            return null;
        }
        return latest.subtract(earliest).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean looksLikeFollowUp(String question) {
        String trimmed = question.trim();
        return trimmed.length() <= 8
                || trimmed.startsWith("那")
                || trimmed.startsWith("这个")
                || trimmed.startsWith("这样")
                || trimmed.startsWith("然后")
                || trimmed.startsWith("如果");
    }

    private boolean looksLikeDailyPlanningQuestion(String question) {
        return looksLikeMealQuestion(question.toLowerCase(Locale.ROOT), "今天", "早餐", "午餐", "晚餐", "加餐", "外卖");
    }

    private boolean looksLikeMealQuestion(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal average(List<BigDecimal> values, int days) {
        BigDecimal total = values.stream()
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal remaining(BigDecimal target, BigDecimal actual) {
        return zeroSafe(target).subtract(zeroSafe(actual)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
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
        return "你";
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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

    private String formatSignedDecimal(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        String prefix = value.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        return prefix + formatDecimal(value);
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

    private String limitMessage(String content) {
        if (!StringUtils.hasText(content) || content.length() <= MAX_MESSAGE_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_MESSAGE_LENGTH) + "\n\n如果你愿意，我可以继续往下细化。";
    }

    private AdvisorMessageResponse toResponse(AdvisorMessage message) {
        return AdvisorMessageResponse.builder()
                .id(message.getId())
                .role(StringUtils.hasText(message.getRole()) ? message.getRole() : "ASSISTANT")
                .content(message.getContent())
                .references(readReferences(message.getReferencesJson()))
                .executionDetail(null)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private record GeneratedReply(String content,
                                  List<AdvisorReferenceResponse> references,
                                  AgentExecutionDetailResponse executionDetail) {
    }

    private record AdvisorContext(
            String displayName,
            String goalType,
            BigDecimal targetCalories,
            BigDecimal targetProtein,
            BigDecimal averageCalories,
            BigDecimal averageProtein,
            BigDecimal todayCalories,
            BigDecimal todayProtein,
            int recordedDays,
            BigDecimal latestWeightKg,
            BigDecimal weightDeltaKg,
            String dietaryPreference,
            String allergies,
            String medicalNotes,
            MealPlan todayPlan
    ) {
    }
}
