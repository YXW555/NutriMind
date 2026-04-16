package com.yxw.food.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.bootstrap.food-graph-enabled", havingValue = "true", matchIfMissing = true)
public class FoodGraphBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodGraphBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public FoodGraphBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        long before = countRelations();

        Long dietaryGuideId = upsertKnowledgeSource(
                "中国居民膳食指南（2022）",
                "中国营养学会",
                "GUIDELINE",
                2022,
                "https://dg.cnsoc.org/",
                "HIGH",
                "用于支撑食物搭配、均衡饮食和健康人群膳食建议。"
        );
        Long whoId = upsertKnowledgeSource(
                "WHO Healthy Diet Fact Sheet",
                "World Health Organization",
                "GUIDELINE",
                2020,
                "https://www.who.int/news-room/fact-sheets/detail/healthy-diet",
                "HIGH",
                "用于支撑健康饮食、脂肪和糖摄入控制等国际通用建议。"
        );

        upsertGoalToNutrient("减脂", "goal:fat-loss", "LIMITS", "脂肪", "nutrient:fat",
                "减脂阶段应控制高脂食物摄入，优先关注总能量与脂肪占比。", whoId, 10);
        upsertGoalToNutrient("减脂", "goal:fat-loss", "RECOMMENDS", "蛋白质", "nutrient:protein",
                "减脂期间适当提高蛋白质比例有助于维持饱腹感和肌肉量。", dietaryGuideId, 11);
        upsertGoalToNutrient("增肌", "goal:muscle-gain", "RECOMMENDS", "蛋白质", "nutrient:protein",
                "增肌目标应保证优质蛋白摄入和全天分配。", dietaryGuideId, 12);
        upsertGoalToNutrient("控糖", "goal:glycemic-control", "RECOMMENDS", "膳食纤维", "nutrient:fiber",
                "膳食纤维有助于延缓糖吸收，适合控糖场景。", whoId, 13);

        upsertConditionToFood("乳糖不耐受", "condition:lactose-intolerance", "SHOULD_LIMIT", "牛奶",
                "牛奶乳糖含量较高，乳糖不耐受人群应谨慎选择。", whoId, 20);
        upsertConditionToFood("高脂控制", "condition:lipid-control", "SHOULD_LIMIT", "炸鸡腿",
                "油炸食物脂肪密度较高，不利于日常控脂。", whoId, 21);

        upsertFoodToFood("鸡腿肉", "CAN_REPLACE", "鸡胸肉",
                "在追求更高蛋白、较低脂肪时，鸡胸肉通常是鸡腿肉的更优替代。", dietaryGuideId, 30);
        upsertFoodToFood("米饭", "CAN_REPLACE", "燕麦片",
                "主食替换为更高膳食纤维选项时，燕麦片更适合控糖与饱腹管理。", dietaryGuideId, 31);
        upsertFoodToFood("鸡腿肉", "PAIR_WITH", "西兰花",
                "优质蛋白搭配高纤维蔬菜，更利于形成均衡一餐。", dietaryGuideId, 40);
        upsertFoodToFood("三文鱼", "PAIR_WITH", "西兰花",
                "鱼类与蔬菜搭配有助于改善膳食结构。", dietaryGuideId, 41);
        upsertFoodToFood("燕麦片", "PAIR_WITH", "牛奶",
                "常见早餐搭配，可形成更完整的能量与蛋白质组合。", dietaryGuideId, 42);

        long relationCount = countRelations();
        long added = Math.max(relationCount - before, 0);
        jdbcTemplate.update("""
                INSERT INTO food_graph_sync_logs(sync_type, status, detail, node_count, relation_count)
                VALUES ('BOOTSTRAP', 'SUCCESS', ?, ?, ?)
                """, "Food graph bootstrap completed.", 0, relationCount);
        log.info("Food graph bootstrap completed, current relation count: {}, newly added: {}", relationCount, added);
    }

    private Long upsertKnowledgeSource(String title,
                                       String organization,
                                       String sourceType,
                                       Integer publishYear,
                                       String sourceUrl,
                                       String credibilityLevel,
                                       String summary) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_sources(title, organization, source_type, publish_year, source_url, credibility_level, summary, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                organization = VALUES(organization),
                source_type = VALUES(source_type),
                publish_year = VALUES(publish_year),
                source_url = VALUES(source_url),
                credibility_level = VALUES(credibility_level),
                summary = VALUES(summary),
                status = VALUES(status)
                """, title, organization, sourceType, publishYear, sourceUrl, credibilityLevel, summary);
        return jdbcTemplate.queryForObject("SELECT id FROM knowledge_sources WHERE title = ? LIMIT 1", Long.class, title);
    }

    private void upsertGoalToNutrient(String goalName,
                                      String goalKey,
                                      String relationType,
                                      String nutrientName,
                                      String nutrientKey,
                                      String evidenceSummary,
                                      Long knowledgeSourceId,
                                      int sortOrder) {
        upsertRelation("GOAL", goalKey, null, goalName, relationType,
                "NUTRIENT", nutrientKey, null, nutrientName,
                null, evidenceSummary, knowledgeSourceId, sortOrder);
    }

    private void upsertConditionToFood(String conditionName,
                                       String conditionKey,
                                       String relationType,
                                       String foodName,
                                       String evidenceSummary,
                                       Long knowledgeSourceId,
                                       int sortOrder) {
        Long foodId = findFoodId(foodName);
        if (foodId == null) {
            return;
        }
        upsertRelation("CONDITION", conditionKey, null, conditionName, relationType,
                "FOOD", "food:" + foodId, foodId, foodName,
                null, evidenceSummary, knowledgeSourceId, sortOrder);
    }

    private void upsertFoodToFood(String sourceFoodName,
                                  String relationType,
                                  String targetFoodName,
                                  String evidenceSummary,
                                  Long knowledgeSourceId,
                                  int sortOrder) {
        Long sourceFoodId = findFoodId(sourceFoodName);
        Long targetFoodId = findFoodId(targetFoodName);
        if (sourceFoodId == null || targetFoodId == null) {
            return;
        }
        upsertRelation("FOOD", "food:" + sourceFoodId, sourceFoodId, sourceFoodName, relationType,
                "FOOD", "food:" + targetFoodId, targetFoodId, targetFoodName,
                null, evidenceSummary, knowledgeSourceId, sortOrder);
    }

    private void upsertRelation(String sourceType,
                                String sourceKey,
                                Long sourceRefId,
                                String sourceName,
                                String relationType,
                                String targetType,
                                String targetKey,
                                Long targetRefId,
                                String targetName,
                                String relationValue,
                                String evidenceSummary,
                                Long knowledgeSourceId,
                                int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO food_graph_relations(source_type, source_key, source_ref_id, source_name,
                                                 relation_type, target_type, target_key, target_ref_id, target_name,
                                                 relation_value, evidence_summary, knowledge_source_id, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                source_ref_id = VALUES(source_ref_id),
                source_name = VALUES(source_name),
                target_ref_id = VALUES(target_ref_id),
                target_name = VALUES(target_name),
                relation_value = VALUES(relation_value),
                evidence_summary = VALUES(evidence_summary),
                knowledge_source_id = VALUES(knowledge_source_id),
                sort_order = VALUES(sort_order),
                status = VALUES(status)
                """,
                sourceType, sourceKey, sourceRefId, sourceName,
                relationType, targetType, targetKey, targetRefId, targetName,
                relationValue, evidenceSummary, knowledgeSourceId, sortOrder);
    }

    private Long findFoodId(String foodName) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM food_basics
                WHERE name = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), foodName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long countRelations() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM food_graph_relations
                WHERE status = 1
                """, Long.class);
        return count == null ? 0L : count;
    }
}
