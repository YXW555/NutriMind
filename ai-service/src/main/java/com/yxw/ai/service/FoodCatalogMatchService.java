package com.yxw.ai.service;

import com.yxw.ai.client.FoodCatalogClient;
import com.yxw.ai.dto.RecognitionCandidateResponse;
import com.yxw.ai.engine.RecognitionEngineCandidate;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FoodCatalogMatchService {

    private static final int SEARCH_PAGE_SIZE = 6;
    private static final int MAX_SEARCH_TERMS = 6;
    private static final int MAX_MATCHES_PER_CANDIDATE = 2;

    private static final BigDecimal LABEL_TERM_BONUS = new BigDecimal("0.18");
    private static final BigDecimal CANONICAL_TERM_BONUS = new BigDecimal("0.16");
    private static final BigDecimal ALIAS_TERM_BONUS = new BigDecimal("0.13");
    private static final BigDecimal KEYWORD_TERM_BONUS = new BigDecimal("0.10");

    private static final BigDecimal EXACT_NAME_BONUS = new BigDecimal("0.24");
    private static final BigDecimal PARTIAL_NAME_BONUS = new BigDecimal("0.16");
    private static final BigDecimal CATEGORY_BONUS = new BigDecimal("0.08");
    private static final BigDecimal BASE_CATALOG_BONUS = new BigDecimal("0.03");

    private final FoodCatalogClient foodCatalogClient;

    public FoodCatalogMatchService(FoodCatalogClient foodCatalogClient) {
        this.foodCatalogClient = foodCatalogClient;
    }

    public List<RecognitionCandidateResponse> mapEngineCandidates(List<RecognitionEngineCandidate> engineCandidates,
                                                                  int topK) {
        List<RecognitionCandidateResponse> results = new ArrayList<>();
        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        Map<String, List<FoodNutritionSnapshot>> searchCache = new HashMap<>();

        for (RecognitionEngineCandidate engineCandidate : engineCandidates) {
            if (engineCandidate == null || !hasAnyCandidateTerm(engineCandidate)) {
                continue;
            }

            List<ScoredFoodMatch> matches = searchFoodsForCandidate(engineCandidate, selectedIds, searchCache);
            int perCandidateLimit = Math.min(MAX_MATCHES_PER_CANDIDATE, Math.max(1, topK - results.size()));

            for (ScoredFoodMatch match : matches) {
                FoodNutritionSnapshot food = match.food();
                if (food.getId() == null || selectedIds.contains(food.getId())) {
                    continue;
                }

                results.add(RecognitionCandidateResponse.fromFood(
                        food,
                        normalizeConfidence(match.score()),
                        match.reason()
                ));
                selectedIds.add(food.getId());
                perCandidateLimit--;

                if (results.size() >= topK) {
                    return results;
                }
                if (perCandidateLimit <= 0) {
                    break;
                }
            }
        }

        if (results.size() < topK) {
            results.addAll(fallbackCatalogCandidates(topK - results.size(), selectedIds));
        }

        return results;
    }

    private boolean hasAnyCandidateTerm(RecognitionEngineCandidate candidate) {
        return StringUtils.hasText(candidate.getLabel())
                || StringUtils.hasText(candidate.getCanonicalLabel())
                || (candidate.getAliases() != null && !candidate.getAliases().isEmpty())
                || (candidate.getSearchKeywords() != null && !candidate.getSearchKeywords().isEmpty());
    }

    private List<ScoredFoodMatch> searchFoodsForCandidate(RecognitionEngineCandidate candidate,
                                                          LinkedHashSet<Long> selectedIds,
                                                          Map<String, List<FoodNutritionSnapshot>> searchCache) {
        List<SearchToken> searchTokens = buildSearchTokens(candidate);
        LinkedHashMap<Long, ScoredFoodMatch> bestMatches = new LinkedHashMap<>();
        BigDecimal baseConfidence = normalizeConfidence(candidate.getConfidence());

        for (SearchToken searchToken : searchTokens) {
            List<FoodNutritionSnapshot> foods = searchCache.computeIfAbsent(
                    normalizeText(searchToken.term()),
                    key -> foodCatalogClient.searchFoods(searchToken.term(), SEARCH_PAGE_SIZE)
            );

            for (FoodNutritionSnapshot food : foods) {
                if (food.getId() == null || selectedIds.contains(food.getId())) {
                    continue;
                }

                BigDecimal score = baseConfidence
                        .add(searchToken.termBonus())
                        .add(computeLexicalBonus(food, searchToken.term()));
                String reason = buildMatchReason(candidate, searchToken);

                ScoredFoodMatch current = bestMatches.get(food.getId());
                if (current == null || current.score().compareTo(score) < 0) {
                    bestMatches.put(food.getId(), new ScoredFoodMatch(food, score, reason));
                }
            }
        }

        return bestMatches.values().stream()
                .sorted(Comparator.comparing(ScoredFoodMatch::score).reversed()
                        .thenComparing(match -> safeValue(match.food().getName())))
                .toList();
    }

    private List<SearchToken> buildSearchTokens(RecognitionEngineCandidate candidate) {
        LinkedHashMap<String, SearchToken> tokens = new LinkedHashMap<>();

        addSearchToken(tokens, candidate.getLabel(), LABEL_TERM_BONUS, "识别标签");
        addSearchToken(tokens, candidate.getCanonicalLabel(), CANONICAL_TERM_BONUS, "标准标签");
        addSearchTokens(tokens, candidate.getAliases(), ALIAS_TERM_BONUS, "别名");
        addSearchTokens(tokens, candidate.getSearchKeywords(), KEYWORD_TERM_BONUS, "搜索关键词");

        return tokens.values().stream()
                .limit(MAX_SEARCH_TERMS)
                .toList();
    }

    private void addSearchTokens(LinkedHashMap<String, SearchToken> tokens,
                                 List<String> values,
                                 BigDecimal bonus,
                                 String reason) {
        if (values == null || values.isEmpty()) {
            return;
        }

        for (String value : values) {
            addSearchToken(tokens, value, bonus, reason);
        }
    }

    private void addSearchToken(LinkedHashMap<String, SearchToken> tokens,
                                String value,
                                BigDecimal bonus,
                                String reason) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        String term = value.trim();
        String normalized = normalizeText(term);
        if (!StringUtils.hasText(normalized)) {
            return;
        }

        SearchToken current = tokens.get(normalized);
        if (current == null || current.termBonus().compareTo(bonus) < 0) {
            tokens.put(normalized, new SearchToken(term, bonus, reason));
        }
    }

    private BigDecimal computeLexicalBonus(FoodNutritionSnapshot food, String keyword) {
        String normalizedKeyword = normalizeText(keyword);
        String normalizedName = normalizeText(food.getName());
        String normalizedCategory = normalizeText(food.getCategory());

        if (!StringUtils.hasText(normalizedKeyword)) {
            return BASE_CATALOG_BONUS;
        }
        if (normalizedKeyword.equals(normalizedName)) {
            return EXACT_NAME_BONUS;
        }
        if (normalizedName.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedName)) {
            return PARTIAL_NAME_BONUS;
        }
        if (StringUtils.hasText(normalizedCategory)
                && (normalizedCategory.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedCategory))) {
            return CATEGORY_BONUS;
        }
        return BASE_CATALOG_BONUS;
    }

    private String buildMatchReason(RecognitionEngineCandidate candidate, SearchToken searchToken) {
        String engineReason = StringUtils.hasText(candidate.getMatchReason())
                ? candidate.getMatchReason().trim()
                : "视觉候选召回";
        return engineReason + "，使用" + searchToken.reason() + "“" + searchToken.term() + "”匹配食物库";
    }

    private List<RecognitionCandidateResponse> fallbackCatalogCandidates(int count,
                                                                         LinkedHashSet<Long> selectedIds) {
        List<FoodNutritionSnapshot> foods = foodCatalogClient.searchFoods(null, count + selectedIds.size() + 3);
        List<RecognitionCandidateResponse> results = new ArrayList<>();

        for (FoodNutritionSnapshot food : foods) {
            if (food.getId() == null || selectedIds.contains(food.getId())) {
                continue;
            }

            BigDecimal confidence = scoreFallbackConfidence(results.size());
            results.add(RecognitionCandidateResponse.fromFood(
                    food,
                    confidence,
                    "食物库兜底推荐"
            ));
            selectedIds.add(food.getId());

            if (results.size() >= count) {
                break;
            }
        }

        return results;
    }

    private BigDecimal normalizeConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return new BigDecimal("0.50");
        }

        BigDecimal normalized = confidence;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            normalized = BigDecimal.ZERO;
        }
        if (normalized.compareTo(BigDecimal.ONE) > 0) {
            normalized = BigDecimal.ONE;
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreFallbackConfidence(int index) {
        BigDecimal value = new BigDecimal("0.52")
                .subtract(new BigDecimal("0.05").multiply(BigDecimal.valueOf(index)));
        if (value.compareTo(new BigDecimal("0.35")) < 0) {
            value = new BigDecimal("0.35");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private record SearchToken(String term, BigDecimal termBonus, String reason) {
    }

    private record ScoredFoodMatch(FoodNutritionSnapshot food, BigDecimal score, String reason) {
    }
}
