package com.yxw.ai.service;

import com.yxw.ai.client.FoodCatalogClient;
import com.yxw.ai.client.dto.FoodCatalogCreateRequest;
import com.yxw.ai.dto.RecognitionCandidateResponse;
import com.yxw.ai.dto.RecognizedConceptResponse;
import com.yxw.ai.engine.RecognitionEngineCandidate;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.common.core.security.SecurityContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FoodCatalogMatchService {

    private static final int SEARCH_PAGE_SIZE = 10;
    private static final int MAX_SEARCH_TERMS = 8;
    private static final int MAX_MATCHES = 4;
    private static final int MAX_ESTIMATE_REFERENCES = 3;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-z]+");

    private final FoodCatalogClient foodCatalogClient;

    public FoodCatalogMatchService(FoodCatalogClient foodCatalogClient) {
        this.foodCatalogClient = foodCatalogClient;
    }

    public RecognitionCatalogResult mapEngineCandidates(List<RecognitionEngineCandidate> engineCandidates, int topK) {
        List<RecognitionEngineCandidate> orderedCandidates = sanitizeCandidates(engineCandidates);
        if (orderedCandidates.isEmpty()) {
            return new RecognitionCatalogResult(null, fallbackCatalogCandidates(Math.max(topK, 1), new LinkedHashSet<>()));
        }

        RecognitionEngineCandidate primaryEngineCandidate = orderedCandidates.get(0);
        ResolvedConcept primaryConcept = resolveConcept(primaryEngineCandidate);

        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        List<RecognitionCandidateResponse> matches = new ArrayList<>();

        if (primaryConcept != null) {
            matches.addAll(searchByConcept(primaryConcept, primaryEngineCandidate, Math.max(topK, 1), selectedIds));
        }

        for (int index = 1; index < orderedCandidates.size() && matches.size() < Math.max(topK, 1); index++) {
            RecognitionEngineCandidate candidate = orderedCandidates.get(index);
            ResolvedConcept concept = resolveConcept(candidate);
            if (concept == null || sameConcept(primaryConcept, concept)) {
                continue;
            }
            matches.addAll(searchByConcept(concept, candidate, Math.max(topK, 1) - matches.size(), selectedIds));
        }

        EstimatedFoodResult estimatedFood = maybeBuildEstimatedFood(primaryConcept, primaryEngineCandidate, matches);

        List<RecognitionCandidateResponse> finalCandidates = new ArrayList<>();
        if (estimatedFood != null) {
            finalCandidates.add(estimatedFood.candidate());
            if (estimatedFood.candidate().getId() != null) {
                selectedIds.add(estimatedFood.candidate().getId());
            }
        }

        for (RecognitionCandidateResponse match : matches) {
            if (finalCandidates.size() >= Math.max(topK, 1)) {
                break;
            }
            if (match.getId() != null && selectedIds.contains(match.getId())) {
                continue;
            }
            finalCandidates.add(match);
            if (match.getId() != null) {
                selectedIds.add(match.getId());
            }
        }

        if (finalCandidates.isEmpty()) {
            finalCandidates.addAll(fallbackCatalogCandidates(Math.max(topK, 1), selectedIds));
        }

        return new RecognitionCatalogResult(
                primaryConcept == null ? null : primaryConcept.toResponse(),
                finalCandidates.stream().limit(Math.max(topK, 1)).toList()
        );
    }

    private List<RecognitionEngineCandidate> sanitizeCandidates(List<RecognitionEngineCandidate> engineCandidates) {
        if (engineCandidates == null || engineCandidates.isEmpty()) {
            return List.of();
        }

        return engineCandidates.stream()
                .filter(Objects::nonNull)
                .filter(this::hasAnyCandidateTerm)
                .sorted(Comparator.comparing(
                        (RecognitionEngineCandidate candidate) -> normalizeConfidence(candidate.getConfidence()))
                        .reversed())
                .toList();
    }

    private boolean hasAnyCandidateTerm(RecognitionEngineCandidate candidate) {
        return StringUtils.hasText(candidate.getLabel())
                || StringUtils.hasText(candidate.getCanonicalLabel())
                || (candidate.getAliases() != null && !candidate.getAliases().isEmpty())
                || (candidate.getSearchKeywords() != null && !candidate.getSearchKeywords().isEmpty());
    }

    private List<RecognitionCandidateResponse> searchByConcept(ResolvedConcept concept,
                                                               RecognitionEngineCandidate engineCandidate,
                                                               int limit,
                                                               LinkedHashSet<Long> selectedIds) {
        if (limit <= 0) {
            return List.of();
        }

        LinkedHashMap<Long, ScoredFoodMatch> bestMatches = new LinkedHashMap<>();
        List<String> searchTerms = buildSearchTerms(concept, engineCandidate);

        for (String term : searchTerms) {
            List<FoodNutritionSnapshot> foods = foodCatalogClient.searchFoods(term, SEARCH_PAGE_SIZE);
            for (FoodNutritionSnapshot food : foods) {
                if (food == null || food.getId() == null || selectedIds.contains(food.getId())) {
                    continue;
                }

                BigDecimal score = computeMatchScore(food, term, engineCandidate);
                ScoredFoodMatch current = bestMatches.get(food.getId());
                if (current == null || current.score().compareTo(score) < 0) {
                    bestMatches.put(food.getId(), new ScoredFoodMatch(
                            food,
                            score,
                            buildMatchReason(term, concept.displayName())
                    ));
                }
            }
        }

        List<RecognitionCandidateResponse> results = new ArrayList<>();
        for (ScoredFoodMatch item : bestMatches.values().stream()
                .sorted(Comparator.comparing(ScoredFoodMatch::score).reversed())
                .toList()) {
            if (results.size() >= Math.min(limit, MAX_MATCHES)) {
                break;
            }
            results.add(RecognitionCandidateResponse.fromFood(
                    item.food(),
                    item.score(),
                    item.reason(),
                    safeText(engineCandidate.getLabel()),
                    safeText(engineCandidate.getCanonicalLabel()),
                    concept.searchKeywords()
            ));
        }

        return results;
    }

    private EstimatedFoodResult maybeBuildEstimatedFood(ResolvedConcept concept,
                                                        RecognitionEngineCandidate engineCandidate,
                                                        List<RecognitionCandidateResponse> matches) {
        if (concept == null || engineCandidate == null) {
            return null;
        }

        String preferredName = firstNonBlank(
                engineCandidate.getCanonicalLabel(),
                engineCandidate.getLabel(),
                concept.displayName()
        );
        if (!StringUtils.hasText(preferredName)) {
            return null;
        }

        if (hasStrongExactMatch(preferredName, matches)) {
            return null;
        }

        List<RecognitionCandidateResponse> estimateBase = matches.stream()
                .filter(Objects::nonNull)
                .limit(MAX_ESTIMATE_REFERENCES)
                .toList();
        if (estimateBase.isEmpty()) {
            return null;
        }

        FoodNutritionSnapshot exactFood = findExactFood(preferredName);
        FoodNutritionSnapshot food = exactFood != null
                ? exactFood
                : createEstimatedFood(preferredName, concept, estimateBase);
        if (food == null || food.getId() == null) {
            return null;
        }

        String estimateSummary = estimateBase.stream()
                .map(RecognitionCandidateResponse::getName)
                .filter(StringUtils::hasText)
                .limit(MAX_ESTIMATE_REFERENCES)
                .distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse(null);

        RecognitionCandidateResponse candidate = RecognitionCandidateResponse.fromFood(
                food,
                normalizeConfidence(firstNonNull(engineCandidate.getConfidence(), new BigDecimal("0.78"))),
                buildEstimatedMatchReason(concept.displayName(), estimateSummary),
                safeText(engineCandidate.getLabel()),
                safeText(engineCandidate.getCanonicalLabel()),
                concept.searchKeywords()
        );
        candidate.setEstimated(Boolean.TRUE);
        candidate.setEstimateSourceSummary(estimateSummary);
        candidate.setManualConfirmationRequired(Boolean.TRUE);
        return new EstimatedFoodResult(candidate);
    }

    private FoodNutritionSnapshot createEstimatedFood(String preferredName,
                                                      ResolvedConcept concept,
                                                      List<RecognitionCandidateResponse> estimateBase) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carbohydrate = BigDecimal.ZERO;

        for (RecognitionCandidateResponse item : estimateBase) {
            BigDecimal weight = normalizeConfidence(firstNonNull(item.getConfidence(), new BigDecimal("0.60")));
            totalWeight = totalWeight.add(weight);
            calories = calories.add(zeroSafe(item.getCalories()).multiply(weight));
            protein = protein.add(zeroSafe(item.getProtein()).multiply(weight));
            fat = fat.add(zeroSafe(item.getFat()).multiply(weight));
            carbohydrate = carbohydrate.add(zeroSafe(item.getCarbohydrate()).multiply(weight));
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        String category = estimateBase.stream()
                .map(RecognitionCandidateResponse::getCategory)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(concept.displayName());
        Long ownerUserId = SecurityContextUtils.currentUserId().orElse(null);
        String sourceType = ownerUserId == null ? "SYSTEM_ESTIMATED" : "USER_ESTIMATED";

        return foodCatalogClient.createFood(FoodCatalogCreateRequest.builder()
                .name(preferredName.trim())
                .category(category)
                .ownerUserId(ownerUserId)
                .sourceType(sourceType)
                .unit("100g")
                .calories(divide(calories, totalWeight))
                .protein(divide(protein, totalWeight))
                .fat(divide(fat, totalWeight))
                .carbohydrate(divide(carbohydrate, totalWeight))
                .fiber(BigDecimal.ZERO)
                .status(1)
                .build());
    }

    private boolean hasStrongExactMatch(String preferredName, List<RecognitionCandidateResponse> matches) {
        String normalizedPreferred = normalizeText(preferredName);
        if (!StringUtils.hasText(normalizedPreferred)) {
            return false;
        }

        for (RecognitionCandidateResponse match : matches) {
            if (match == null || !StringUtils.hasText(match.getName())) {
                continue;
            }

            String normalizedName = normalizeText(match.getName());
            if (normalizedPreferred.equals(normalizedName)) {
                return true;
            }

            if (isNearExactCompositeMatch(normalizedPreferred, normalizedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearExactCompositeMatch(String preferredName, String candidateName) {
        if (!StringUtils.hasText(preferredName) || !StringUtils.hasText(candidateName)) {
            return false;
        }

        int preferredLength = preferredName.length();
        int candidateLength = candidateName.length();
        int minLength = Math.min(preferredLength, candidateLength);
        int maxLength = Math.max(preferredLength, candidateLength);

        if (minLength < 4) {
            return false;
        }

        if (preferredName.contains(candidateName) || candidateName.contains(preferredName)) {
            return minLength * 1.0 / maxLength >= 0.8;
        }

        return false;
    }

    private FoodNutritionSnapshot findExactFood(String preferredName) {
        String normalizedPreferred = normalizeText(preferredName);
        return foodCatalogClient.searchFoods(preferredName, 5).stream()
                .filter(Objects::nonNull)
                .filter(item -> normalizedPreferred.equals(normalizeText(item.getName())))
                .findFirst()
                .orElse(null);
    }

    private List<RecognitionCandidateResponse> fallbackCatalogCandidates(int count, LinkedHashSet<Long> selectedIds) {
        List<FoodNutritionSnapshot> foods = foodCatalogClient.searchFoods(null, count + selectedIds.size() + 3);
        List<RecognitionCandidateResponse> results = new ArrayList<>();

        for (FoodNutritionSnapshot food : foods) {
            if (food == null || food.getId() == null || selectedIds.contains(food.getId())) {
                continue;
            }
            results.add(RecognitionCandidateResponse.fromFood(
                    food,
                    scoreFallbackConfidence(results.size()),
                    "catalog fallback",
                    null,
                    null,
                    List.of()
            ));
            selectedIds.add(food.getId());
            if (results.size() >= count) {
                break;
            }
        }

        return results;
    }

    private ResolvedConcept resolveConcept(RecognitionEngineCandidate candidate) {
        if (candidate == null) {
            return null;
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addTerms(terms, candidate.getSearchKeywords());
        addTerms(terms, candidate.getAliases());
        addTerms(terms, List.of(candidate.getCanonicalLabel(), candidate.getLabel()));

        Set<String> englishTokens = extractEnglishTokens(candidate);
        if (containsAny(englishTokens, "salad")) {
            addTerms(terms, List.of("沙拉", "蔬菜沙拉", "鸡肉沙拉"));
        }
        if (containsAny(englishTokens, "egg", "omelet", "omelette")) {
            addTerms(terms, List.of("鸡蛋", "炒鸡蛋", "蛋类"));
        }
        if (containsAny(englishTokens, "rice", "friedrice", "porridge", "oatmeal")) {
            addTerms(terms, List.of("米饭", "主食", "燕麦"));
        }
        if (containsAny(englishTokens, "noodle", "noodles", "pasta")) {
            addTerms(terms, List.of("面条", "面食"));
        }
        if (containsAny(englishTokens, "milk", "yogurt", "dairy")) {
            addTerms(terms, List.of("牛奶", "酸奶", "奶制品"));
        }
        if (containsAny(englishTokens, "banana", "apple", "orange", "fruit")) {
            addTerms(terms, List.of("水果", "香蕉", "苹果", "橙子"));
        }
        if (containsAny(englishTokens, "broccoli", "cucumber", "tomato", "potato", "vegetable")) {
            addTerms(terms, List.of("蔬菜", "西兰花", "黄瓜", "番茄", "土豆"));
        }
        if (containsAny(englishTokens, "fish", "salmon", "tuna")) {
            addTerms(terms, List.of("鱼", "鱼肉", "三文鱼"));
        }
        if (containsAny(englishTokens, "shrimp", "prawn", "seafood")) {
            addTerms(terms, List.of("虾仁", "虾", "海鲜"));
        }
        if (containsAny(englishTokens, "beef", "steak")) {
            addTerms(terms, List.of("牛肉", "牛排"));
        }
        if (containsAny(englishTokens, "pork", "lamb", "red", "meat")) {
            addTerms(terms, List.of("红肉", "猪肉", "牛肉", "羊肉"));
        }
        if (containsAny(englishTokens, "chicken", "poultry", "drumstick", "wing", "white")) {
            addTerms(terms, List.of("鸡肉", "鸡胸肉", "鸡腿肉", "白肉"));
        }

        List<String> keywords = terms.stream()
                .filter(StringUtils::hasText)
                .limit(MAX_SEARCH_TERMS)
                .toList();
        if (keywords.isEmpty()) {
            return null;
        }

        String displayName = firstNonBlank(candidate.getCanonicalLabel(), candidate.getLabel(), keywords.get(0));
        return ResolvedConcept.builder()
                .displayName(displayName)
                .rawLabel(safeText(candidate.getLabel()))
                .canonicalLabel(safeText(candidate.getCanonicalLabel()))
                .confidence(normalizeConfidence(candidate.getConfidence()))
                .matchReason(safeText(candidate.getMatchReason()))
                .searchKeywords(keywords)
                .aliases(candidate.getAliases() == null ? List.of() : candidate.getAliases())
                .generic(Boolean.FALSE)
                .build();
    }

    private List<String> buildSearchTerms(ResolvedConcept concept, RecognitionEngineCandidate candidate) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addTerms(terms, concept.searchKeywords());
        addTerms(terms, candidate.getSearchKeywords());
        addTerms(terms, candidate.getAliases());
        addTerms(terms, List.of(candidate.getCanonicalLabel(), candidate.getLabel(), concept.displayName()));
        return terms.stream().limit(MAX_SEARCH_TERMS).toList();
    }

    private BigDecimal computeMatchScore(FoodNutritionSnapshot food,
                                         String searchTerm,
                                         RecognitionEngineCandidate engineCandidate) {
        BigDecimal baseConfidence = normalizeConfidence(engineCandidate.getConfidence());
        String normalizedTerm = normalizeText(searchTerm);
        String normalizedName = normalizeText(food.getName());
        String normalizedCategory = normalizeText(food.getCategory());

        BigDecimal lexicalScore = BigDecimal.ZERO;
        if (normalizedTerm.equals(normalizedName)) {
            lexicalScore = new BigDecimal("1.00");
        } else if (normalizedName.contains(normalizedTerm) || normalizedTerm.contains(normalizedName)) {
            lexicalScore = new BigDecimal("0.82");
        } else if (StringUtils.hasText(normalizedCategory)
                && (normalizedCategory.contains(normalizedTerm) || normalizedTerm.contains(normalizedCategory))) {
            lexicalScore = new BigDecimal("0.56");
        }

        BigDecimal score = baseConfidence.multiply(new BigDecimal("0.65"))
                .add(lexicalScore.multiply(new BigDecimal("0.35")));
        if (score.compareTo(new BigDecimal("0.98")) > 0) {
            score = new BigDecimal("0.98");
        }
        if (score.compareTo(new BigDecimal("0.35")) < 0) {
            score = new BigDecimal("0.35");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildMatchReason(String term, String conceptDisplayName) {
        return "matched from concept " + conceptDisplayName + " with keyword: " + term;
    }

    private Set<String> extractEnglishTokens(RecognitionEngineCandidate candidate) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        extractEnglishTokensInto(tokens, candidate.getLabel());
        extractEnglishTokensInto(tokens, candidate.getCanonicalLabel());
        if (candidate.getAliases() != null) {
            candidate.getAliases().forEach(value -> extractEnglishTokensInto(tokens, value));
        }
        if (candidate.getSearchKeywords() != null) {
            candidate.getSearchKeywords().forEach(value -> extractEnglishTokensInto(tokens, value));
        }
        return tokens;
    }

    private void extractEnglishTokensInto(Set<String> tokens, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        tokens.add(normalized.replaceAll("[\\s\\p{Punct}]+", ""));
    }

    private boolean containsAny(Set<String> tokens, String... expected) {
        for (String item : expected) {
            if (tokens.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameConcept(ResolvedConcept left, ResolvedConcept right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeText(left.displayName()).equals(normalizeText(right.displayName()));
    }

    private void addTerms(LinkedHashSet<String> target, Collection<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                target.add(value.trim());
            }
        }
    }

    private BigDecimal scoreFallbackConfidence(int index) {
        BigDecimal value = new BigDecimal("0.52")
                .subtract(new BigDecimal("0.05").multiply(BigDecimal.valueOf(index)));
        if (value.compareTo(new BigDecimal("0.35")) < 0) {
            value = new BigDecimal("0.35");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
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

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return zeroSafe(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal firstNonNull(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String buildEstimatedMatchReason(String conceptDisplayName, String estimateSummary) {
        if (StringUtils.hasText(estimateSummary)) {
            return "auto-estimated from concept " + conceptDisplayName + " using similar foods: " + estimateSummary;
        }
        return "auto-estimated from concept " + conceptDisplayName;
    }

    public record RecognitionCatalogResult(RecognizedConceptResponse recognizedConcept,
                                           List<RecognitionCandidateResponse> candidates) {
    }

    private record SearchableFood(FoodNutritionSnapshot food, BigDecimal score, String reason) {
    }

    private record ScoredFoodMatch(FoodNutritionSnapshot food, BigDecimal score, String reason) {
    }

    private record EstimatedFoodResult(RecognitionCandidateResponse candidate) {
    }

    @lombok.Builder
    private record ResolvedConcept(String displayName,
                                   String rawLabel,
                                   String canonicalLabel,
                                   BigDecimal confidence,
                                   String matchReason,
                                   List<String> searchKeywords,
                                   List<String> aliases,
                                   Boolean generic) {

        private RecognizedConceptResponse toResponse() {
            return RecognizedConceptResponse.builder()
                    .displayName(displayName)
                    .rawLabel(rawLabel)
                    .canonicalLabel(canonicalLabel)
                    .confidence(confidence)
                    .matchReason(matchReason)
                    .searchKeywords(searchKeywords == null ? List.of() : searchKeywords)
                    .aliases(aliases == null ? List.of() : aliases)
                    .generic(generic)
                    .build();
        }
    }
}
