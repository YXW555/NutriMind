package com.yxw.meal.service;

import com.yxw.meal.client.FoodCatalogClient;
import com.yxw.meal.client.dto.FoodGraphProfileSnapshot;
import com.yxw.meal.client.dto.FoodGraphRelationSnapshot;
import com.yxw.meal.dto.AdvisorReferenceResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FoodGraphContextService {

    private final FoodCatalogClient foodCatalogClient;

    public FoodGraphContextService(FoodCatalogClient foodCatalogClient) {
        this.foodCatalogClient = foodCatalogClient;
    }

    public GraphContext buildContext(String query, List<String> anchorFoods, int relationLimit) {
        int limit = Math.max(1, Math.min(relationLimit, 12));
        LinkedHashMap<Long, FoodGraphRelationSnapshot> unique = new LinkedHashMap<>();

        for (String token : extractTokens(query)) {
            try {
                List<FoodGraphRelationSnapshot> relations = foodCatalogClient.listGraphRelations(token, null, Math.min(limit, 6));
                appendRelations(unique, relations, limit);
                if (unique.size() >= limit) {
                    break;
                }
            } catch (RuntimeException ignored) {
                // Keep the graph layer best-effort to avoid blocking core flows.
            }
        }

        if (anchorFoods != null) {
            for (String foodName : anchorFoods) {
                if (!StringUtils.hasText(foodName)) {
                    continue;
                }
                try {
                    var matched = foodCatalogClient.pickBestMatch(foodName);
                    if (matched == null || matched.getId() == null) {
                        continue;
                    }
                    FoodGraphProfileSnapshot profile = foodCatalogClient.getFoodGraphProfile(matched.getId(), Math.min(limit, 6));
                    appendRelations(unique, profile.getRelations(), limit);
                    if (unique.size() >= limit) {
                        break;
                    }
                } catch (RuntimeException ignored) {
                    // Keep the graph layer best-effort to avoid blocking core flows.
                }
            }
        }

        List<FoodGraphRelationSnapshot> relations = new ArrayList<>(unique.values());
        return new GraphContext(
                relations,
                buildPromptSummary(relations),
                buildActionHints(relations, 3),
                buildPlanHints(relations, 4),
                toAdvisorReferences(relations),
                toPlanReferences(relations)
        );
    }

    public List<AdvisorReferenceResponse> toAdvisorReferences(List<FoodGraphRelationSnapshot> relations) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<AdvisorReferenceResponse> references = new ArrayList<>();
        for (FoodGraphRelationSnapshot relation : relations) {
            references.add(AdvisorReferenceResponse.builder()
                    .title(relation.getSourceName() + " " + relation.getRelationType() + " " + relation.getTargetName())
                    .section("知识图谱关系")
                    .excerpt(firstNonBlank(relation.getEvidenceSummary(), relation.getRelationValue(), "图谱关系命中"))
                    .authority(relation.getKnowledgeSourceTitle())
                    .sourceName(firstNonBlank(relation.getKnowledgeSourceTitle(), "营养知识图谱"))
                    .sourceUrl(null)
                    .build());
        }
        return references;
    }

    public List<String> toPlanReferences(List<FoodGraphRelationSnapshot> relations) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (FoodGraphRelationSnapshot relation : relations) {
            references.add(relation.getSourceName() + " " + relation.getRelationType() + " " + relation.getTargetName());
        }
        return uniqueStrings(references, 6);
    }

    private String buildPromptSummary(List<FoodGraphRelationSnapshot> relations) {
        if (relations == null || relations.isEmpty()) {
            return "No graph relation matched.";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < relations.size(); i++) {
            FoodGraphRelationSnapshot relation = relations.get(i);
            lines.add((i + 1) + ". "
                    + relation.getSourceName()
                    + " -> "
                    + relation.getRelationType()
                    + " -> "
                    + relation.getTargetName()
                    + " | "
                    + firstNonBlank(relation.getEvidenceSummary(), relation.getKnowledgeSourceTitle(), "graph hit"));
        }
        return String.join("\n", lines);
    }

    private List<String> buildActionHints(List<FoodGraphRelationSnapshot> relations, int limit) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        for (FoodGraphRelationSnapshot relation : relations) {
            String hint = switch (safe(relation.getRelationType())) {
                case "CAN_REPLACE" -> relation.getSourceName() + "可以用" + relation.getTargetName() + "作为更稳妥的替代选择。";
                case "PAIR_WITH" -> relation.getSourceName() + "更适合搭配" + relation.getTargetName() + "一起安排。";
                case "SHOULD_LIMIT", "LIMITS" -> relation.getSourceName() + "相关场景下需要额外关注" + relation.getTargetName() + "的控制。";
                case "RECOMMENDS" -> relation.getSourceName() + "场景下应优先关注" + relation.getTargetName() + "。";
                default -> firstNonBlank(relation.getEvidenceSummary(), relation.getSourceName() + "与" + relation.getTargetName() + "存在图谱关联。");
            };
            hints.add(hint);
        }
        return uniqueStrings(hints, limit);
    }

    private List<String> buildPlanHints(List<FoodGraphRelationSnapshot> relations, int limit) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        for (FoodGraphRelationSnapshot relation : relations) {
            String value = firstNonBlank(relation.getEvidenceSummary(), relation.getRelationValue());
            if (StringUtils.hasText(value)) {
                hints.add(value);
            } else {
                hints.add(relation.getSourceName() + " " + relation.getRelationType() + " " + relation.getTargetName());
            }
        }
        return uniqueStrings(hints, limit);
    }

    private void appendRelations(Map<Long, FoodGraphRelationSnapshot> unique,
                                 List<FoodGraphRelationSnapshot> relations,
                                 int limit) {
        if (relations == null || relations.isEmpty()) {
            return;
        }
        for (FoodGraphRelationSnapshot relation : relations) {
            if (relation == null || relation.getId() == null) {
                continue;
            }
            unique.putIfAbsent(relation.getId(), relation);
            if (unique.size() >= limit) {
                return;
            }
        }
    }

    private List<String> extractTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        String normalized = query.replaceAll("[\\r\\n]+", " ").trim();
        values.add(normalized);
        for (String token : normalized.split("[\\s,，。；;、/]+")) {
            String value = token.trim();
            if (value.length() >= 2) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
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

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record GraphContext(
            List<FoodGraphRelationSnapshot> relations,
            String promptSummary,
            List<String> actionHints,
            List<String> planHints,
            List<AdvisorReferenceResponse> advisorReferences,
            List<String> planReferences
    ) {
    }
}
