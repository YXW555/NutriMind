package com.yxw.food.service.impl;

import com.yxw.food.config.FoodGraphNeo4jProperties;
import com.yxw.food.config.FoodGraphNeo4jSyncService;
import com.yxw.food.dto.FoodGraphOverviewResponse;
import com.yxw.food.dto.FoodGraphProfileResponse;
import com.yxw.food.dto.FoodGraphRelationResponse;
import com.yxw.food.dto.FoodGraphRelationTypeSummaryResponse;
import com.yxw.food.dto.FoodGraphSyncResponse;
import com.yxw.food.dto.KnowledgeSourceResponse;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
public class FoodGraphNeo4jService implements FoodGraphNeo4jSyncService {

    private static final Logger log = LoggerFactory.getLogger(FoodGraphNeo4jService.class);

    private static final String GRAPH_SPACE = "FOOD_GRAPH";

    private final JdbcTemplate jdbcTemplate;
    private final FoodGraphNeo4jProperties properties;
    private final Driver driver;

    public FoodGraphNeo4jService(JdbcTemplate jdbcTemplate,
                                 FoodGraphNeo4jProperties properties,
                                 ObjectProvider<Driver> driverProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.driver = driverProvider.getIfAvailable();
    }

    public boolean isReady() {
        if (!properties.isEnabled() || driver == null) {
            return false;
        }
        try (Session session = openSession()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception ex) {
            log.warn("Neo4j graph service is not ready yet: {}", ex.getMessage());
            return false;
        }
    }

    public Optional<FoodGraphOverviewResponse> getOverview(Long foodNodeCount,
                                                           Long knowledgeSourceCount,
                                                           Long syncLogCount) {
        if (!isReady()) {
            return Optional.empty();
        }
        try (Session session = openSession()) {
            Long graphNodeCount = runRead(session, tx -> singleLong(tx, """
                    MATCH (n:GraphNode {graphSpace: $graphSpace})
                    RETURN count(n) AS total
                    """, Map.of("graphSpace", GRAPH_SPACE)));
            Long relationCount = runRead(session, tx -> singleLong(tx, """
                    MATCH (:GraphNode {graphSpace: $graphSpace})-[r:RELATED {graphSpace: $graphSpace}]->(:GraphNode {graphSpace: $graphSpace})
                    RETURN count(r) AS total
                    """, Map.of("graphSpace", GRAPH_SPACE)));

            List<FoodGraphRelationTypeSummaryResponse> relationTypeSummary = runRead(session, tx -> {
                Result result = tx.run("""
                        MATCH (:GraphNode {graphSpace: $graphSpace})-[r:RELATED {graphSpace: $graphSpace}]->(:GraphNode {graphSpace: $graphSpace})
                        RETURN r.relationType AS relationType, count(r) AS total
                        ORDER BY total DESC, relationType ASC
                        """, Map.of("graphSpace", GRAPH_SPACE));
                List<FoodGraphRelationTypeSummaryResponse> rows = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    rows.add(FoodGraphRelationTypeSummaryResponse.builder()
                            .relationType(readString(record, "relationType"))
                            .count(readLong(record, "total"))
                            .build());
                }
                return rows;
            });

            List<FoodGraphRelationResponse> sampleRelations = listRelationsInternal(session, null, null, 8);

            return Optional.of(FoodGraphOverviewResponse.builder()
                    .backend("Neo4j")
                    .neo4jReady(true)
                    .foodNodeCount(foodNodeCount)
                    .graphNodeCount(graphNodeCount)
                    .relationCount(relationCount)
                    .knowledgeSourceCount(knowledgeSourceCount)
                    .syncLogCount(syncLogCount)
                    .relationTypeSummary(relationTypeSummary)
                    .sampleRelations(sampleRelations)
                    .build());
        } catch (Exception ex) {
            log.warn("Read graph overview from Neo4j failed, fallback to relational graph: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<List<FoodGraphRelationResponse>> listRelations(String keyword, String relationType, Integer size) {
        if (!isReady()) {
            return Optional.empty();
        }
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 100));
        try (Session session = openSession()) {
            return Optional.of(listRelationsInternal(session, keyword, relationType, limit));
        } catch (Exception ex) {
            log.warn("List graph relations from Neo4j failed, fallback to relational graph: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<FoodGraphProfileResponse> getFoodGraphProfile(Long foodId,
                                                                  Integer size,
                                                                  FoodGraphProfileResponse baseProfile) {
        if (!isReady()) {
            return Optional.empty();
        }
        int limit = Math.max(1, Math.min(size == null ? 20 : size, 100));
        try (Session session = openSession()) {
            List<FoodGraphRelationResponse> relations = runRead(session, tx -> {
                Result result = tx.run("""
                        MATCH (n:GraphNode {graphSpace: $graphSpace, nodeType: 'FOOD', refId: $foodId})
                        OPTIONAL MATCH (n)-[r:RELATED {graphSpace: $graphSpace}]-(m:GraphNode {graphSpace: $graphSpace})
                        RETURN r.relationId AS id,
                               startNode(r).nodeType AS sourceType,
                               startNode(r).nodeKeyValue AS sourceKey,
                               startNode(r).refId AS sourceRefId,
                               startNode(r).name AS sourceName,
                               r.relationType AS relationType,
                               endNode(r).nodeType AS targetType,
                               endNode(r).nodeKeyValue AS targetKey,
                               endNode(r).refId AS targetRefId,
                               endNode(r).name AS targetName,
                               r.relationValue AS relationValue,
                               r.evidenceSummary AS evidenceSummary,
                               r.knowledgeSourceId AS knowledgeSourceId,
                               r.knowledgeSourceTitle AS knowledgeSourceTitle,
                               r.status AS status
                        ORDER BY coalesce(r.sortOrder, 0) ASC, r.relationId ASC
                        LIMIT $size
                        """, Map.of(
                        "graphSpace", GRAPH_SPACE,
                        "foodId", foodId,
                        "size", limit
                ));
                List<FoodGraphRelationResponse> rows = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    if (record.get("id").isNull()) {
                        continue;
                    }
                    rows.add(mapRelation(record));
                }
                return rows;
            });

            baseProfile.setRelationCount((long) relations.size());
            baseProfile.setRelations(relations);
            return Optional.of(baseProfile);
        } catch (Exception ex) {
            log.warn("Read food graph profile from Neo4j failed, fallback to relational graph: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<List<KnowledgeSourceResponse>> listKnowledgeSources() {
        if (!isReady()) {
            return Optional.empty();
        }
        try (Session session = openSession()) {
            return Optional.of(runRead(session, tx -> {
                Result result = tx.run("""
                        MATCH (s:KnowledgeSource {graphSpace: $graphSpace})
                        RETURN s.sourceId AS id,
                               s.title AS title,
                               s.organization AS organization,
                               s.sourceType AS sourceType,
                               s.publishYear AS publishYear,
                               s.sourceUrl AS sourceUrl,
                               s.credibilityLevel AS credibilityLevel,
                               s.summary AS summary,
                               s.status AS status
                        ORDER BY s.publishYear DESC, s.sourceId ASC
                        """, Map.of("graphSpace", GRAPH_SPACE));
                List<KnowledgeSourceResponse> rows = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    rows.add(KnowledgeSourceResponse.builder()
                            .id(readLong(record, "id"))
                            .title(readString(record, "title"))
                            .organization(readString(record, "organization"))
                            .sourceType(readString(record, "sourceType"))
                            .publishYear(readInteger(record, "publishYear"))
                            .sourceUrl(readString(record, "sourceUrl"))
                            .credibilityLevel(readString(record, "credibilityLevel"))
                            .summary(readString(record, "summary"))
                            .status(readInteger(record, "status"))
                            .build());
                }
                return rows;
            }));
        } catch (Exception ex) {
            log.warn("Read knowledge sources from Neo4j failed, fallback to relational graph: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public FoodGraphSyncResponse syncFromRelationalGraph() {
        if (!properties.isEnabled() || driver == null) {
            writeSyncLog("NEO4J_EXPORT", "SKIPPED", "Neo4j graph sync skipped because Neo4j is disabled.", 0, 0);
            return FoodGraphSyncResponse.builder()
                    .backend("MySQL")
                    .status("SKIPPED")
                    .detail("Neo4j 未启用，当前继续使用关系型图谱。")
                    .nodeCount(0L)
                    .relationCount(0L)
                    .build();
        }

        List<Map<String, Object>> knowledgeSources = jdbcTemplate.queryForList("""
                SELECT id, title, organization, source_type, publish_year, source_url, credibility_level, summary, status
                FROM knowledge_sources
                WHERE status = 1
                ORDER BY id ASC
                """);
        List<Map<String, Object>> relations = jdbcTemplate.queryForList("""
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
                       r.sort_order,
                       r.status
                FROM food_graph_relations r
                LEFT JOIN knowledge_sources ks ON r.knowledge_source_id = ks.id
                WHERE r.status = 1
                ORDER BY r.sort_order ASC, r.id ASC
                """);

        try (Session session = openSession()) {
            runWrite(session, tx -> {
                tx.run("""
                        CREATE CONSTRAINT nutrimind_graph_node_key IF NOT EXISTS
                        FOR (n:GraphNode)
                        REQUIRE n.nodeKey IS UNIQUE
                        """);
                tx.run("""
                        CREATE CONSTRAINT nutrimind_graph_source_key IF NOT EXISTS
                        FOR (s:KnowledgeSource)
                        REQUIRE s.sourceId IS UNIQUE
                        """);
                tx.run("""
                        MATCH (n)
                        WHERE (n:GraphNode OR n:KnowledgeSource) AND n.graphSpace = $graphSpace
                        DETACH DELETE n
                        """, Map.of("graphSpace", GRAPH_SPACE));

                for (Map<String, Object> source : knowledgeSources) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("graphSpace", GRAPH_SPACE);
                    params.put("sourceId", source.get("id"));
                    params.put("title", safeValue(source.get("title")));
                    params.put("organization", safeValue(source.get("organization")));
                    params.put("sourceType", safeValue(source.get("source_type")));
                    params.put("publishYear", source.get("publish_year"));
                    params.put("sourceUrl", safeValue(source.get("source_url")));
                    params.put("credibilityLevel", safeValue(source.get("credibility_level")));
                    params.put("summary", safeValue(source.get("summary")));
                    params.put("status", source.get("status"));
                    tx.run("""
                            MERGE (s:KnowledgeSource {sourceId: $sourceId})
                            SET s.graphSpace = $graphSpace,
                                s.title = $title,
                                s.organization = $organization,
                                s.sourceType = $sourceType,
                                s.publishYear = $publishYear,
                                s.sourceUrl = $sourceUrl,
                                s.credibilityLevel = $credibilityLevel,
                                s.summary = $summary,
                                s.status = $status
                            """, params);
                }

                for (Map<String, Object> relation : relations) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("graphSpace", GRAPH_SPACE);
                    params.put("sourceNodeKey", buildNodeKey(readString(relation, "source_type"), readString(relation, "source_key")));
                    params.put("sourceType", readString(relation, "source_type"));
                    params.put("sourceKeyValue", readString(relation, "source_key"));
                    params.put("sourceRefId", relation.get("source_ref_id"));
                    params.put("sourceName", readString(relation, "source_name"));
                    params.put("targetNodeKey", buildNodeKey(readString(relation, "target_type"), readString(relation, "target_key")));
                    params.put("targetType", readString(relation, "target_type"));
                    params.put("targetKeyValue", readString(relation, "target_key"));
                    params.put("targetRefId", relation.get("target_ref_id"));
                    params.put("targetName", readString(relation, "target_name"));
                    params.put("relationKey", "RELATION:" + relation.get("id"));
                    params.put("relationId", relation.get("id"));
                    params.put("relationType", readString(relation, "relation_type"));
                    params.put("relationValue", safeValue(relation.get("relation_value")));
                    params.put("evidenceSummary", safeValue(relation.get("evidence_summary")));
                    params.put("knowledgeSourceId", relation.get("knowledge_source_id"));
                    params.put("knowledgeSourceTitle", safeValue(relation.get("knowledge_source_title")));
                    params.put("sortOrder", relation.get("sort_order"));
                    params.put("status", relation.get("status"));

                    tx.run("""
                            MERGE (s:GraphNode {nodeKey: $sourceNodeKey})
                            SET s.graphSpace = $graphSpace,
                                s.nodeType = $sourceType,
                                s.nodeKeyValue = $sourceKeyValue,
                                s.refId = $sourceRefId,
                                s.name = $sourceName
                            MERGE (t:GraphNode {nodeKey: $targetNodeKey})
                            SET t.graphSpace = $graphSpace,
                                t.nodeType = $targetType,
                                t.nodeKeyValue = $targetKeyValue,
                                t.refId = $targetRefId,
                                t.name = $targetName
                            MERGE (s)-[r:RELATED {relationKey: $relationKey}]->(t)
                            SET r.graphSpace = $graphSpace,
                                r.relationId = $relationId,
                                r.relationType = $relationType,
                                r.relationValue = $relationValue,
                                r.evidenceSummary = $evidenceSummary,
                                r.knowledgeSourceId = $knowledgeSourceId,
                                r.knowledgeSourceTitle = $knowledgeSourceTitle,
                                r.sortOrder = $sortOrder,
                                r.status = $status
                            WITH s, t, r
                            OPTIONAL MATCH (ks:KnowledgeSource {graphSpace: $graphSpace, sourceId: $knowledgeSourceId})
                            FOREACH (_ IN CASE WHEN ks IS NULL THEN [] ELSE [1] END |
                                MERGE (s)-[:SUPPORTED_BY {graphSpace: $graphSpace, relationKey: $relationKey}]->(ks)
                                MERGE (t)-[:SUPPORTED_BY {graphSpace: $graphSpace, relationKey: $relationKey}]->(ks)
                            )
                            """, params);
                }
                return null;
            });
            writeSyncLog(
                    "NEO4J_EXPORT",
                    "SUCCESS",
                    "Neo4j graph sync completed.",
                    countGraphNodes(knowledgeSources, relations),
                    relations.size()
            );
            log.info("Neo4j graph sync completed, exported {} relations.", relations.size());
            return FoodGraphSyncResponse.builder()
                    .backend("Neo4j")
                    .status("SUCCESS")
                    .detail("Neo4j 图谱同步完成。")
                    .nodeCount(countGraphNodes(knowledgeSources, relations))
                    .relationCount((long) relations.size())
                    .build();
        } catch (Exception ex) {
            log.warn("Neo4j graph sync failed: {}", ex.getMessage(), ex);
            writeSyncLog("NEO4J_EXPORT", "FAILED", abbreviate("Neo4j graph sync failed: " + ex.getMessage()), 0, relations.size());
            return FoodGraphSyncResponse.builder()
                    .backend("Neo4j")
                    .status("FAILED")
                    .detail("Neo4j 图谱同步失败：" + ex.getMessage())
                    .nodeCount(0L)
                    .relationCount((long) relations.size())
                    .build();
        }
    }

    private List<FoodGraphRelationResponse> listRelationsInternal(Session session,
                                                                 String keyword,
                                                                 String relationType,
                                                                 int limit) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedRelationType = StringUtils.hasText(relationType) ? relationType.trim() : null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("graphSpace", GRAPH_SPACE);
        params.put("relationType", normalizedRelationType);
        params.put("keyword", normalizedKeyword);
        params.put("size", limit);
        return runRead(session, tx -> {
            Result result = tx.run("""
                    MATCH (s:GraphNode {graphSpace: $graphSpace})-[r:RELATED {graphSpace: $graphSpace}]->(t:GraphNode {graphSpace: $graphSpace})
                    WHERE ($relationType IS NULL OR r.relationType = $relationType)
                      AND ($keyword IS NULL OR s.name CONTAINS $keyword OR t.name CONTAINS $keyword OR coalesce(r.evidenceSummary, '') CONTAINS $keyword)
                    RETURN r.relationId AS id,
                           s.nodeType AS sourceType,
                           s.nodeKeyValue AS sourceKey,
                           s.refId AS sourceRefId,
                           s.name AS sourceName,
                           r.relationType AS relationType,
                           t.nodeType AS targetType,
                           t.nodeKeyValue AS targetKey,
                           t.refId AS targetRefId,
                           t.name AS targetName,
                           r.relationValue AS relationValue,
                           r.evidenceSummary AS evidenceSummary,
                           r.knowledgeSourceId AS knowledgeSourceId,
                           r.knowledgeSourceTitle AS knowledgeSourceTitle,
                           r.status AS status
                    ORDER BY coalesce(r.sortOrder, 0) ASC, r.relationId ASC
                    LIMIT $size
                    """, params);
            List<FoodGraphRelationResponse> rows = new ArrayList<>();
            while (result.hasNext()) {
                rows.add(mapRelation(result.next()));
            }
            return rows;
        });
    }

    private FoodGraphRelationResponse mapRelation(Record record) {
        return FoodGraphRelationResponse.builder()
                .id(readLong(record, "id"))
                .sourceType(readString(record, "sourceType"))
                .sourceKey(readString(record, "sourceKey"))
                .sourceRefId(readLong(record, "sourceRefId"))
                .sourceName(readString(record, "sourceName"))
                .relationType(readString(record, "relationType"))
                .targetType(readString(record, "targetType"))
                .targetKey(readString(record, "targetKey"))
                .targetRefId(readLong(record, "targetRefId"))
                .targetName(readString(record, "targetName"))
                .relationValue(readString(record, "relationValue"))
                .evidenceSummary(readString(record, "evidenceSummary"))
                .knowledgeSourceId(readLong(record, "knowledgeSourceId"))
                .knowledgeSourceTitle(readString(record, "knowledgeSourceTitle"))
                .status(readInteger(record, "status"))
                .build();
    }

    private Long singleLong(TransactionContext tx, String cypher, Map<String, Object> params) {
        Result result = tx.run(cypher, params);
        if (!result.hasNext()) {
            return 0L;
        }
        Value value = result.next().values().get(0);
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    private <T> T runRead(Session session, Function<TransactionContext, T> callback) {
        return session.executeRead(callback::apply);
    }

    private <T> T runWrite(Session session, Function<TransactionContext, T> callback) {
        return session.executeWrite(callback::apply);
    }

    private Session openSession() {
        String database = StringUtils.hasText(properties.getDatabase()) ? properties.getDatabase().trim() : "neo4j";
        return driver.session(SessionConfig.forDatabase(database));
    }

    private long countGraphNodes(List<Map<String, Object>> knowledgeSources, List<Map<String, Object>> relations) {
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        for (Map<String, Object> relation : relations) {
            nodes.put(buildNodeKey(readString(relation, "source_type"), readString(relation, "source_key")), Boolean.TRUE);
            nodes.put(buildNodeKey(readString(relation, "target_type"), readString(relation, "target_key")), Boolean.TRUE);
        }
        return nodes.size() + knowledgeSources.size();
    }

    private String buildNodeKey(String type, String key) {
        return type + "|" + key;
    }

    private void writeSyncLog(String syncType, String status, String detail, int nodeCount, int relationCount) {
        jdbcTemplate.update("""
                INSERT INTO food_graph_sync_logs(sync_type, status, detail, node_count, relation_count)
                VALUES (?, ?, ?, ?, ?)
                """, syncType, status, detail, nodeCount, relationCount);
    }

    private void writeSyncLog(String syncType, String status, String detail, long nodeCount, long relationCount) {
        writeSyncLog(syncType, status, detail, (int) nodeCount, (int) relationCount);
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private String safeValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String readString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String readString(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private Long readLong(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Integer readInteger(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asInt();
    }
}
