package com.yxw.ai.service;

import com.yxw.ai.client.FoodCatalogClient;
import com.yxw.ai.dto.RecognitionCandidateResponse;
import com.yxw.ai.dto.RecognizedConceptResponse;
import com.yxw.ai.engine.RecognitionEngineCandidate;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
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
    private static final int MAX_SEARCH_TERMS = 10;
    private static final int MAX_MATCHES_PER_PLAN = 4;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-z]+");

    private static final BigDecimal PRIMARY_CONCEPT_BONUS = new BigDecimal("0.20");
    private static final BigDecimal SECONDARY_CONCEPT_BONUS = new BigDecimal("0.12");
    private static final BigDecimal DIRECT_KEYWORD_BONUS = new BigDecimal("0.10");
    private static final BigDecimal DIRECT_ALIAS_BONUS = new BigDecimal("0.08");
    private static final BigDecimal MAX_BONUS_SCORE = new BigDecimal("0.46");

    private static final Map<String, ConceptTemplate> EXACT_CONCEPT_TEMPLATES = Map.ofEntries(
            Map.entry("whitemeat", ConceptTemplate.generic("白肉类", List.of("鸡肉", "鸡胸肉", "鸡腿肉", "禽肉", "鱼肉"))),
            Map.entry("redmeat", ConceptTemplate.generic("红肉类", List.of("牛肉", "猪肉", "羊肉", "牛排"))),
            Map.entry("mixedmeat", ConceptTemplate.generic("肉类", List.of("鸡肉", "牛肉", "猪肉", "鱼肉"))),
            Map.entry("breadedmeat", ConceptTemplate.generic("裹粉肉类", List.of("炸鸡", "鸡排", "鸡块", "鸡肉"))),
            Map.entry("fish", ConceptTemplate.specific("鱼类", List.of("鱼", "鱼肉", "鱼排"))),
            Map.entry("salmon", ConceptTemplate.specific("鱼类", List.of("三文鱼", "鱼肉", "鱼"))),
            Map.entry("seafood", ConceptTemplate.generic("海鲜类", List.of("海鲜", "鱼肉", "虾仁", "鱼"))),
            Map.entry("shrimp", ConceptTemplate.specific("虾类", List.of("虾仁", "虾", "海鲜"))),
            Map.entry("chicken", ConceptTemplate.specific("鸡肉类", List.of("鸡肉", "鸡胸肉", "鸡腿肉", "鸡排"))),
            Map.entry("poultry", ConceptTemplate.generic("禽肉类", List.of("鸡肉", "鸡胸肉", "鸡腿肉", "鸭肉"))),
            Map.entry("drumstick", ConceptTemplate.specific("鸡腿类", List.of("鸡腿肉", "鸡腿", "鸡肉"))),
            Map.entry("wing", ConceptTemplate.specific("鸡翅类", List.of("鸡翅", "鸡肉"))),
            Map.entry("beef", ConceptTemplate.specific("牛肉类", List.of("牛肉", "牛排", "瘦牛肉"))),
            Map.entry("steak", ConceptTemplate.specific("牛排类", List.of("牛排", "牛肉"))),
            Map.entry("salad", ConceptTemplate.specific("沙拉类", List.of("沙拉", "蔬菜沙拉", "鸡肉沙拉"))),
            Map.entry("sandwich", ConceptTemplate.specific("三明治类", List.of("三明治", "面包", "吐司"))),
            Map.entry("bread", ConceptTemplate.specific("面包类", List.of("面包", "吐司", "三明治"))),
            Map.entry("rice", ConceptTemplate.specific("米饭类", List.of("米饭", "炒饭", "白米饭"))),
            Map.entry("noodles", ConceptTemplate.specific("面食类", List.of("面条", "炒面", "面食"))),
            Map.entry("noodle", ConceptTemplate.specific("面食类", List.of("面条", "炒面", "面食"))),
            Map.entry("egg", ConceptTemplate.specific("鸡蛋类", List.of("鸡蛋", "煮鸡蛋", "蛋"))),
            Map.entry("milk", ConceptTemplate.specific("奶制品", List.of("牛奶", "酸奶"))),
            Map.entry("yogurt", ConceptTemplate.specific("奶制品", List.of("酸奶", "牛奶"))),
            Map.entry("banana", ConceptTemplate.specific("香蕉类", List.of("香蕉"))),
            Map.entry("apple", ConceptTemplate.specific("苹果类", List.of("苹果"))),
            Map.entry("orange", ConceptTemplate.specific("橙子类", List.of("橙子", "橘子"))),
            Map.entry("broccoli", ConceptTemplate.specific("西兰花类", List.of("西兰花", "蔬菜"))),
            Map.entry("vegetable", ConceptTemplate.generic("蔬菜类", List.of("蔬菜", "西兰花", "黄瓜", "番茄"))),
            Map.entry("tomato", ConceptTemplate.specific("番茄类", List.of("番茄", "西红柿"))),
            Map.entry("cucumber", ConceptTemplate.specific("黄瓜类", List.of("黄瓜"))),
            Map.entry("potato", ConceptTemplate.specific("土豆类", List.of("土豆"))),
            Map.entry("tofu", ConceptTemplate.specific("豆腐类", List.of("豆腐"))),
            Map.entry("fruit", ConceptTemplate.generic("水果类", List.of("水果", "香蕉", "苹果", "橙子")))
    );

    private final FoodCatalogClient foodCatalogClient;

    public FoodCatalogMatchService(FoodCatalogClient foodCatalogClient) {
        this.foodCatalogClient = foodCatalogClient;
    }

    public RecognitionCatalogResult mapEngineCandidates(List<RecognitionEngineCandidate> engineCandidates, int topK) {
        List<RecognitionEngineCandidate> orderedCandidates = sanitizeCandidates(engineCandidates);
        if (orderedCandidates.isEmpty()) {
            return new RecognitionCatalogResult(null, fallbackCatalogCandidates(topK, new LinkedHashSet<>()));
        }

        ResolvedConcept primaryConcept = resolveConcept(orderedCandidates.get(0));
        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
        Map<String, List<FoodNutritionSnapshot>> searchCache = new HashMap<>();
        List<RecognitionCandidateResponse> matches = new ArrayList<>();

        if (primaryConcept != null) {
            matches.addAll(searchByConcept(primaryConcept, orderedCandidates.get(0), topK, selectedIds, searchCache));
        }

        if (matches.size() < topK) {
            for (int index = 1; index < orderedCandidates.size(); index++) {
                RecognitionEngineCandidate engineCandidate = orderedCandidates.get(index);
                ResolvedConcept concept = resolveConcept(engineCandidate);
                if (concept == null || sameConcept(primaryConcept, concept)) {
                    continue;
                }

                matches.addAll(searchByConcept(concept, engineCandidate, topK - matches.size(), selectedIds, searchCache));
                if (matches.size() >= topK) {
                    break;
                }
            }
        }

        if (matches.size() < topK) {
            matches.addAll(fallbackCatalogCandidates(topK - matches.size(), selectedIds));
        }

        return new RecognitionCatalogResult(
                primaryConcept == null ? null : primaryConcept.toResponse(),
                matches
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
                                                               LinkedHashSet<Long> selectedIds,
                                                               Map<String, List<FoodNutritionSnapshot>> searchCache) {
        if (limit <= 0) {
            return List.of();
        }

        LinkedHashMap<Long, ScoredFoodMatch> bestMatches = new LinkedHashMap<>();
        BigDecimal baseConfidence = normalizeConfidence(engineCandidate.getConfidence());
        List<SearchPlan> searchPlans = buildSearchPlans(concept, engineCandidate);

        for (SearchPlan searchPlan : searchPlans) {
            String normalizedTerm = normalizeText(searchPlan.term());
            List<FoodNutritionSnapshot> foods = searchCache.computeIfAbsent(
                    normalizedTerm,
                    key -> foodCatalogClient.searchFoods(searchPlan.term(), SEARCH_PAGE_SIZE)
            );

            for (FoodNutritionSnapshot food : foods) {
                if (food.getId() == null || selectedIds.contains(food.getId())) {
                    continue;
                }

                BigDecimal lexicalBonus = computeLexicalBonus(food, searchPlan.term());
                BigDecimal score = computeMatchScore(baseConfidence, searchPlan.bonus(), lexicalBonus);

                ScoredFoodMatch current = bestMatches.get(food.getId());
                if (current == null || current.score().compareTo(score) < 0) {
                    bestMatches.put(food.getId(), new ScoredFoodMatch(
                            food,
                            score,
                            buildMatchReason(concept, engineCandidate, searchPlan)
                    ));
                }
            }
        }

        List<RecognitionCandidateResponse> results = new ArrayList<>();
        for (ScoredFoodMatch match : bestMatches.values().stream()
                .sorted(Comparator.comparing(ScoredFoodMatch::score).reversed()
                        .thenComparing(item -> safeText(item.food().getName())))
                .toList()) {
            FoodNutritionSnapshot food = match.food();
            if (food.getId() == null || selectedIds.contains(food.getId())) {
                continue;
            }

            results.add(RecognitionCandidateResponse.fromFood(
                    food,
                    normalizeConfidence(match.score()),
                    match.reason(),
                    safeText(engineCandidate.getLabel()),
                    safeText(engineCandidate.getCanonicalLabel()),
                    concept.searchKeywords()
            ));
            selectedIds.add(food.getId());

            if (results.size() >= Math.min(limit, MAX_MATCHES_PER_PLAN)) {
                break;
            }
        }
        return results;
    }

    private List<SearchPlan> buildSearchPlans(ResolvedConcept concept, RecognitionEngineCandidate candidate) {
        LinkedHashMap<String, SearchPlan> plans = new LinkedHashMap<>();

        for (String term : concept.searchKeywords()) {
            addSearchPlan(plans, term, PRIMARY_CONCEPT_BONUS, "concept-keyword");
        }

        addSearchPlan(plans, candidate.getCanonicalLabel(), SECONDARY_CONCEPT_BONUS, "canonical-label");
        addSearchPlan(plans, candidate.getLabel(), SECONDARY_CONCEPT_BONUS, "raw-label");
        addSearchPlans(plans, candidate.getSearchKeywords(), DIRECT_KEYWORD_BONUS, "engine-keyword");
        addSearchPlans(plans, candidate.getAliases(), DIRECT_ALIAS_BONUS, "engine-alias");

        return plans.values().stream()
                .limit(MAX_SEARCH_TERMS)
                .toList();
    }

    private void addSearchPlans(LinkedHashMap<String, SearchPlan> plans,
                                Collection<String> values,
                                BigDecimal bonus,
                                String source) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            addSearchPlan(plans, value, bonus, source);
        }
    }

    private void addSearchPlan(LinkedHashMap<String, SearchPlan> plans,
                               String value,
                               BigDecimal bonus,
                               String source) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return;
        }

        SearchPlan current = plans.get(normalized);
        if (current == null || current.bonus().compareTo(bonus) < 0) {
            plans.put(normalized, new SearchPlan(value.trim(), bonus, source));
        }
    }

    private ResolvedConcept resolveConcept(RecognitionEngineCandidate candidate) {
        if (candidate == null) {
            return null;
        }

        String canonicalLabel = firstNonBlank(candidate.getCanonicalLabel(), candidate.getLabel());
        String normalizedLabel = normalizeText(canonicalLabel);
        ConceptTemplate exactTemplate = EXACT_CONCEPT_TEMPLATES.get(normalizedLabel);
        if (exactTemplate != null) {
            return ResolvedConcept.fromTemplate(candidate, exactTemplate);
        }

        Set<String> englishTokens = extractEnglishTokens(candidate);
        ResolvedConcept semanticConcept = resolveSemanticConcept(candidate, englishTokens);
        if (semanticConcept != null) {
            return semanticConcept;
        }

        LinkedHashSet<String> queryTerms = new LinkedHashSet<>();
        addTerms(queryTerms, candidate.getSearchKeywords());
        addTerms(queryTerms, candidate.getAliases());
        addTerms(queryTerms, List.of(candidate.getCanonicalLabel(), candidate.getLabel()));
        if (queryTerms.isEmpty()) {
            return null;
        }

        return ResolvedConcept.builder()
                .displayName(firstNonBlank(candidate.getCanonicalLabel(), candidate.getLabel(), "识别候选"))
                .rawLabel(safeText(candidate.getLabel()))
                .canonicalLabel(safeText(candidate.getCanonicalLabel()))
                .confidence(normalizeConfidence(candidate.getConfidence()))
                .matchReason(safeText(candidate.getMatchReason()))
                .searchKeywords(queryTerms.stream().limit(MAX_SEARCH_TERMS).toList())
                .aliases(candidate.getAliases() == null ? List.of() : candidate.getAliases())
                .generic(Boolean.FALSE)
                .build();
    }

    private ResolvedConcept resolveSemanticConcept(RecognitionEngineCandidate candidate, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }

        List<ConceptTemplate> matchedTemplates = new ArrayList<>();

        if (tokens.contains("salad")) {
            matchedTemplates.add(ConceptTemplate.specific("沙拉类", List.of("沙拉", "蔬菜沙拉", "鸡肉沙拉")));
        }
        if (containsAny(tokens, "sandwich", "bread", "toast", "bun", "baozi", "mantou")) {
            matchedTemplates.add(ConceptTemplate.specific("面点类", List.of("三明治", "面包", "吐司", "包子", "馒头")));
        }
        if (containsAny(tokens, "rice", "friedrice", "porridge", "congee", "oatmeal", "cereal")) {
            matchedTemplates.add(ConceptTemplate.specific("主食类", List.of("米饭", "炒饭", "粥", "燕麦")));
        }
        if (containsAny(tokens, "noodle", "noodles", "pasta")) {
            matchedTemplates.add(ConceptTemplate.specific("面食类", List.of("面条", "炒面", "面食")));
        }
        if (containsAny(tokens, "egg", "omelet", "omelette")) {
            matchedTemplates.add(ConceptTemplate.specific("鸡蛋类", List.of("鸡蛋", "煮鸡蛋", "蛋")));
        }
        if (containsAny(tokens, "milk", "yogurt", "dairy", "cheese")) {
            matchedTemplates.add(ConceptTemplate.specific("奶制品", List.of("牛奶", "酸奶")));
        }
        if (containsAny(tokens, "banana", "apple", "orange", "fruit")) {
            matchedTemplates.add(ConceptTemplate.generic("水果类", List.of("香蕉", "苹果", "橙子", "水果")));
        }
        if (containsAny(tokens, "broccoli", "cucumber", "tomato", "potato", "vegetable")) {
            matchedTemplates.add(ConceptTemplate.generic("蔬菜类", List.of("西兰花", "黄瓜", "番茄", "土豆", "蔬菜")));
        }
        if (containsAny(tokens, "tofu", "bean")) {
            matchedTemplates.add(ConceptTemplate.specific("豆制品", List.of("豆腐")));
        }
        if (containsAny(tokens, "fish", "salmon", "tuna")) {
            matchedTemplates.add(ConceptTemplate.specific("鱼类", List.of("鱼", "鱼肉", "三文鱼")));
        }
        if (containsAny(tokens, "shrimp", "prawn", "seafood")) {
            matchedTemplates.add(ConceptTemplate.generic("海鲜类", List.of("虾仁", "虾", "海鲜", "鱼肉")));
        }
        if (containsAny(tokens, "beef", "steak")) {
            matchedTemplates.add(ConceptTemplate.specific("牛肉类", List.of("牛肉", "牛排", "瘦牛肉")));
        }
        if (containsAny(tokens, "pork", "lamb") || (tokens.contains("red") && tokens.contains("meat"))) {
            matchedTemplates.add(ConceptTemplate.generic("红肉类", List.of("牛肉", "猪肉", "羊肉")));
        }
        if (containsAny(tokens, "chicken", "poultry", "drumstick", "wing")
                || (tokens.contains("white") && tokens.contains("meat"))) {
            matchedTemplates.add(ConceptTemplate.generic("白肉类", List.of("鸡肉", "鸡胸肉", "鸡腿肉", "禽肉", "鱼肉")));
        }
        if (tokens.contains("meat") && matchedTemplates.isEmpty()) {
            matchedTemplates.add(ConceptTemplate.generic("肉类", List.of("鸡肉", "牛肉", "猪肉", "鱼肉")));
        }

        if (matchedTemplates.isEmpty()) {
            return null;
        }

        if (matchedTemplates.size() == 1) {
            return ResolvedConcept.fromTemplate(candidate, matchedTemplates.get(0));
        }

        LinkedHashSet<String> queryTerms = new LinkedHashSet<>();
        for (ConceptTemplate template : matchedTemplates) {
            addTerms(queryTerms, template.queryTerms());
        }
        return ResolvedConcept.builder()
                .displayName("组合餐食")
                .rawLabel(safeText(candidate.getLabel()))
                .canonicalLabel(safeText(candidate.getCanonicalLabel()))
                .confidence(normalizeConfidence(candidate.getConfidence()))
                .matchReason(safeText(candidate.getMatchReason()))
                .searchKeywords(queryTerms.stream().limit(MAX_SEARCH_TERMS).toList())
                .aliases(candidate.getAliases() == null ? List.of() : candidate.getAliases())
                .generic(Boolean.TRUE)
                .build();
    }

    private boolean containsAny(Set<String> tokens, String... expected) {
        for (String item : expected) {
            if (tokens.contains(item)) {
                return true;
            }
        }
        return false;
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

    private void addTerms(LinkedHashSet<String> target, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                target.add(value.trim());
            }
        }
    }

    private boolean sameConcept(ResolvedConcept left, ResolvedConcept right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeText(left.displayName()).equals(normalizeText(right.displayName()));
    }

    private BigDecimal computeLexicalBonus(FoodNutritionSnapshot food, String keyword) {
        String normalizedKeyword = normalizeText(keyword);
        String normalizedName = normalizeText(food.getName());
        String normalizedCategory = normalizeText(food.getCategory());

        if (!StringUtils.hasText(normalizedKeyword)) {
            return BigDecimal.ZERO;
        }
        if (normalizedKeyword.equals(normalizedName)) {
            return new BigDecimal("0.26");
        }
        if (normalizedName.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedName)) {
            return new BigDecimal("0.18");
        }
        if (StringUtils.hasText(normalizedCategory)
                && (normalizedCategory.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedCategory))) {
            return new BigDecimal("0.08");
        }
        return BigDecimal.ZERO;
    }

    private String buildMatchReason(ResolvedConcept concept,
                                    RecognitionEngineCandidate candidate,
                                    SearchPlan searchPlan) {
        String engineReason = StringUtils.hasText(candidate.getMatchReason())
                ? candidate.getMatchReason().trim()
                : "vision candidate generated";
        return "基于识别概念“" + concept.displayName() + "”召回食物，"
                + "使用 " + searchPlan.source() + " 词条“" + searchPlan.term() + "”；"
                + engineReason;
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

    private BigDecimal computeMatchScore(BigDecimal baseConfidence,
                                         BigDecimal searchPlanBonus,
                                         BigDecimal lexicalBonus) {
        BigDecimal normalizedBase = normalizeConfidence(baseConfidence);
        BigDecimal totalBonus = safeBonus(searchPlanBonus).add(safeBonus(lexicalBonus));
        if (totalBonus.compareTo(MAX_BONUS_SCORE) > 0) {
            totalBonus = MAX_BONUS_SCORE;
        }

        BigDecimal bonusRatio = totalBonus
                .divide(MAX_BONUS_SCORE, 4, RoundingMode.HALF_UP);

        BigDecimal weightedScore = normalizedBase.multiply(new BigDecimal("0.65"))
                .add(bonusRatio.multiply(new BigDecimal("0.35")));

        if (weightedScore.compareTo(new BigDecimal("0.98")) > 0) {
            weightedScore = new BigDecimal("0.98");
        }
        if (weightedScore.compareTo(new BigDecimal("0.35")) < 0) {
            weightedScore = new BigDecimal("0.35");
        }
        return weightedScore.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeBonus(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
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

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public record RecognitionCatalogResult(RecognizedConceptResponse recognizedConcept,
                                           List<RecognitionCandidateResponse> candidates) {
    }

    private record SearchPlan(String term, BigDecimal bonus, String source) {
    }

    private record ScoredFoodMatch(FoodNutritionSnapshot food, BigDecimal score, String reason) {
    }

    private record ConceptTemplate(String displayName, List<String> queryTerms, boolean generic) {

        private static ConceptTemplate generic(String displayName, List<String> queryTerms) {
            return new ConceptTemplate(displayName, queryTerms, true);
        }

        private static ConceptTemplate specific(String displayName, List<String> queryTerms) {
            return new ConceptTemplate(displayName, queryTerms, false);
        }
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

        private static ResolvedConcept fromTemplate(RecognitionEngineCandidate candidate, ConceptTemplate template) {
            LinkedHashSet<String> keywords = new LinkedHashSet<>();
            for (String term : template.queryTerms()) {
                if (StringUtils.hasText(term)) {
                    keywords.add(term.trim());
                }
            }
            if (candidate.getSearchKeywords() != null) {
                candidate.getSearchKeywords().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(keywords::add);
            }

            List<String> aliases = candidate.getAliases() == null ? List.of() : candidate.getAliases().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();

            return ResolvedConcept.builder()
                    .displayName(template.displayName())
                    .rawLabel(candidate.getLabel())
                    .canonicalLabel(candidate.getCanonicalLabel())
                    .confidence(normalizeStaticConfidence(candidate.getConfidence()))
                    .matchReason(candidate.getMatchReason())
                    .searchKeywords(keywords.stream().limit(MAX_SEARCH_TERMS).toList())
                    .aliases(aliases)
                    .generic(template.generic())
                    .build();
        }

        private RecognizedConceptResponse toResponse() {
            return RecognizedConceptResponse.builder()
                    .displayName(displayName)
                    .rawLabel(rawLabel)
                    .canonicalLabel(canonicalLabel)
                    .confidence(confidence)
                    .matchReason(matchReason)
                    .searchKeywords(searchKeywords == null ? List.of() : searchKeywords)
                    .aliases(aliases == null ? List.of() : aliases)
                    .generic(Boolean.TRUE.equals(generic))
                    .build();
        }

        private static BigDecimal normalizeStaticConfidence(BigDecimal confidence) {
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
    }
}
