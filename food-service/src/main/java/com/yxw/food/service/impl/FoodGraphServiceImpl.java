package com.yxw.food.service.impl;

import com.yxw.food.dto.FoodGraphOverviewResponse;
import com.yxw.food.dto.FoodGraphProfileResponse;
import com.yxw.food.dto.FoodGraphRelationResponse;
import com.yxw.food.dto.FoodGraphRelationTypeSummaryResponse;
import com.yxw.food.dto.FoodGraphSyncResponse;
import com.yxw.food.dto.KnowledgeSourceResponse;
import com.yxw.food.service.FoodGraphService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class FoodGraphServiceImpl implements FoodGraphService {

    private final JdbcTemplate jdbcTemplate;
    private final FoodGraphNeo4jService foodGraphNeo4jService;

    public FoodGraphServiceImpl(JdbcTemplate jdbcTemplate,
                                FoodGraphNeo4jService foodGraphNeo4jService) {
        this.jdbcTemplate = jdbcTemplate;
        this.foodGraphNeo4jService = foodGraphNeo4jService;
    }

    @Override
    public FoodGraphOverviewResponse getOverview() {
        Long foodNodeCount = queryForLong("""
                SELECT COUNT(*)
                FROM food_basics
                WHERE status = 1
                """);
        Long relationCount = queryForLong("""
                SELECT COUNT(*)
                FROM food_graph_relations
                WHERE status = 1
                """);
        Long knowledgeSourceCount = queryForLong("""
                SELECT COUNT(*)
                FROM knowledge_sources
                WHERE status = 1
                """);
        Long syncLogCount = queryForLong("""
                SELECT COUNT(*)
                FROM food_graph_sync_logs
                """);

        return foodGraphNeo4jService.getOverview(foodNodeCount, knowledgeSourceCount, syncLogCount)
                .orElseGet(() -> getOverviewFromJdbc(foodNodeCount, knowledgeSourceCount, syncLogCount));
    }

    private FoodGraphOverviewResponse getOverviewFromJdbc(Long foodNodeCount,
                                                          Long knowledgeSourceCount,
                                                          Long syncLogCount) {
        Long relationCount = queryForLong("""
                SELECT COUNT(*)
                FROM food_graph_relations
                WHERE status = 1
                """);
        Long graphNodeCount = queryForLong("""
                SELECT COUNT(*)
                FROM (
                    SELECT source_type, source_key
                    FROM food_graph_relations
                    WHERE status = 1
                    UNION
                    SELECT target_type, target_key
                    FROM food_graph_relations
                    WHERE status = 1
                ) nodes
                """);

        List<FoodGraphRelationTypeSummaryResponse> relationTypeSummary = jdbcTemplate.query("""
                SELECT relation_type, COUNT(*) AS total
                FROM food_graph_relations
                WHERE status = 1
                GROUP BY relation_type
                ORDER BY total DESC, relation_type ASC
                """, (rs, rowNum) -> FoodGraphRelationTypeSummaryResponse.builder()
                .relationType(rs.getString("relation_type"))
                .count(rs.getLong("total"))
                .build());

        List<FoodGraphRelationResponse> sampleRelations = jdbcTemplate.query("""
                SELECT r.id,
                       r.source_type,
                       r.source_key,
                       r.source_ref_id,
                       r.source_name,
                       r.relation_type,
                       r.target_type,
                       r.target_key,
                       r.target_ref_id,
                       r.target_name,
                       r.relation_value,
                       r.evidence_summary,
                       r.knowledge_source_id,
                       ks.title AS knowledge_source_title,
                       r.status
                FROM food_graph_relations r
                LEFT JOIN knowledge_sources ks ON r.knowledge_source_id = ks.id
                WHERE r.status = 1
                ORDER BY r.sort_order ASC, r.id ASC
                LIMIT 8
                """, this::mapRelation);

        return FoodGraphOverviewResponse.builder()
                .backend("MySQL")
                .neo4jReady(foodGraphNeo4jService.isReady())
                .foodNodeCount(foodNodeCount)
                .graphNodeCount(graphNodeCount)
                .relationCount(relationCount)
                .knowledgeSourceCount(knowledgeSourceCount)
                .syncLogCount(syncLogCount)
                .relationTypeSummary(relationTypeSummary)
                .sampleRelations(sampleRelations)
                .build();
    }

    @Override
    public List<FoodGraphRelationResponse> listRelations(String keyword, String relationType, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 100));
        String normalizedKeyword = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : null;
        String normalizedRelationType = StringUtils.hasText(relationType) ? relationType.trim() : null;

        return foodGraphNeo4jService.listRelations(keyword, relationType, limit)
                .orElseGet(() -> listRelationsFromJdbc(normalizedKeyword, normalizedRelationType, limit));
    }

    private List<FoodGraphRelationResponse> listRelationsFromJdbc(String normalizedKeyword,
                                                                 String normalizedRelationType,
                                                                 int limit) {
        return jdbcTemplate.query("""
                SELECT r.id,
                       r.source_type,
                       r.source_key,
                       r.source_ref_id,
                       r.source_name,
                       r.relation_type,
                       r.target_type,
                       r.target_key,
                       r.target_ref_id,
                       r.target_name,
                       r.relation_value,
                       r.evidence_summary,
                       r.knowledge_source_id,
                       ks.title AS knowledge_source_title,
                       r.status
                FROM food_graph_relations r
                LEFT JOIN knowledge_sources ks ON r.knowledge_source_id = ks.id
                WHERE r.status = 1
                  AND (? IS NULL OR r.relation_type = ?)
                  AND (? IS NULL OR r.source_name LIKE ? OR r.target_name LIKE ? OR r.evidence_summary LIKE ?)
                ORDER BY r.sort_order ASC, r.id ASC
                LIMIT ?
                """, this::mapRelation,
                normalizedRelationType, normalizedRelationType,
                normalizedKeyword, normalizedKeyword, normalizedKeyword, normalizedKeyword,
                limit);
    }

    @Override
    public FoodGraphProfileResponse getFoodGraphProfile(Long foodId, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 100));
        List<FoodGraphProfileResponse> foods = jdbcTemplate.query("""
                SELECT b.id,
                       b.name,
                       COALESCE(cat.name, b.category) AS category_name,
                       fc.canonical_name AS concept_name
                FROM food_basics b
                LEFT JOIN food_categories cat ON b.category_id = cat.id
                LEFT JOIN food_concepts fc ON b.concept_id = fc.id
                WHERE b.id = ?
                LIMIT 1
                """, (rs, rowNum) -> FoodGraphProfileResponse.builder()
                .foodId(rs.getLong("id"))
                .foodName(rs.getString("name"))
                .categoryName(rs.getString("category_name"))
                .conceptName(rs.getString("concept_name"))
                .build(), foodId);

        if (foods.isEmpty()) {
            throw new IllegalArgumentException("food not found: " + foodId);
        }

        FoodGraphProfileResponse base = foods.get(0);
        return foodGraphNeo4jService.getFoodGraphProfile(foodId, limit, base)
                .orElseGet(() -> getFoodGraphProfileFromJdbc(foodId, limit, base));
    }

    private FoodGraphProfileResponse getFoodGraphProfileFromJdbc(Long foodId,
                                                                 int limit,
                                                                 FoodGraphProfileResponse base) {
        List<FoodGraphRelationResponse> relations = jdbcTemplate.query("""
                SELECT r.id,
                       r.source_type,
                       r.source_key,
                       r.source_ref_id,
                       r.source_name,
                       r.relation_type,
                       r.target_type,
                       r.target_key,
                       r.target_ref_id,
                       r.target_name,
                       r.relation_value,
                       r.evidence_summary,
                       r.knowledge_source_id,
                       ks.title AS knowledge_source_title,
                       r.status
                FROM food_graph_relations r
                LEFT JOIN knowledge_sources ks ON r.knowledge_source_id = ks.id
                WHERE r.status = 1
                  AND ((r.source_type = 'FOOD' AND r.source_ref_id = ?)
                    OR (r.target_type = 'FOOD' AND r.target_ref_id = ?))
                ORDER BY r.sort_order ASC, r.id ASC
                LIMIT ?
                """, this::mapRelation, foodId, foodId, limit);

        base.setRelationCount((long) relations.size());
        base.setRelations(relations);
        return base;
    }

    @Override
    public List<KnowledgeSourceResponse> listKnowledgeSources() {
        return foodGraphNeo4jService.listKnowledgeSources().orElseGet(this::listKnowledgeSourcesFromJdbc);
    }

    private List<KnowledgeSourceResponse> listKnowledgeSourcesFromJdbc() {
        return jdbcTemplate.query("""
                SELECT id, title, organization, source_type, publish_year, source_url, credibility_level, summary, status
                FROM knowledge_sources
                WHERE status = 1
                ORDER BY publish_year DESC, id ASC
                """, (rs, rowNum) -> KnowledgeSourceResponse.builder()
                .id(rs.getLong("id"))
                .title(rs.getString("title"))
                .organization(rs.getString("organization"))
                .sourceType(rs.getString("source_type"))
                .publishYear((Integer) rs.getObject("publish_year"))
                .sourceUrl(rs.getString("source_url"))
                .credibilityLevel(rs.getString("credibility_level"))
                .summary(rs.getString("summary"))
                .status((Integer) rs.getObject("status"))
                .build());
    }

    @Override
    public FoodGraphSyncResponse syncGraph() {
        return foodGraphNeo4jService.syncFromRelationalGraph();
    }

    private Long queryForLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private FoodGraphRelationResponse mapRelation(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return FoodGraphRelationResponse.builder()
                .id(rs.getLong("id"))
                .sourceType(rs.getString("source_type"))
                .sourceKey(rs.getString("source_key"))
                .sourceRefId((Long) rs.getObject("source_ref_id"))
                .sourceName(rs.getString("source_name"))
                .relationType(rs.getString("relation_type"))
                .targetType(rs.getString("target_type"))
                .targetKey(rs.getString("target_key"))
                .targetRefId((Long) rs.getObject("target_ref_id"))
                .targetName(rs.getString("target_name"))
                .relationValue(rs.getString("relation_value"))
                .evidenceSummary(rs.getString("evidence_summary"))
                .knowledgeSourceId((Long) rs.getObject("knowledge_source_id"))
                .knowledgeSourceTitle(rs.getString("knowledge_source_title"))
                .status((Integer) rs.getObject("status"))
                .build();
    }
}
