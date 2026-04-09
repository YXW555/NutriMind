package com.yxw.food.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.food.dto.FoodUpsertRequest;
import com.yxw.food.entity.FoodBasic;
import com.yxw.food.mapper.FoodBasicMapper;
import com.yxw.food.service.FoodBasicService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class FoodBasicServiceImpl extends ServiceImpl<FoodBasicMapper, FoodBasic> implements FoodBasicService {

    private static final String SEARCH_SQL = """
            SELECT b.id,
                   b.name,
                   b.category,
                   b.unit,
                   b.calories,
                   b.protein,
                   b.fat,
                   b.carbohydrate,
                   b.fiber,
                   b.status,
                   c.canonical_name AS concept_name,
                   c.canonical_name_en AS concept_name_en,
                   cat.name AS category_name,
                   GROUP_CONCAT(DISTINCT fa.alias_name SEPARATOR '||') AS food_aliases,
                   GROUP_CONCAT(DISTINCT cca.alias_name SEPARATOR '||') AS concept_aliases
            FROM food_basics b
            LEFT JOIN food_concepts c ON b.concept_id = c.id
            LEFT JOIN food_categories cat ON b.category_id = cat.id
            LEFT JOIN food_aliases fa ON fa.food_id = b.id
            LEFT JOIN food_concept_aliases cca ON cca.concept_id = c.id
            WHERE b.status = 1
            GROUP BY b.id, b.name, b.category, b.unit, b.calories, b.protein, b.fat, b.carbohydrate, b.fiber, b.status,
                     c.canonical_name, c.canonical_name_en, cat.name
            """;

    private final JdbcTemplate jdbcTemplate;

    public FoodBasicServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<FoodNutritionSnapshot> searchFoods(String keyword, String category, long current, long size) {
        List<SearchableFood> foods = jdbcTemplate.query(SEARCH_SQL, (rs, rowNum) -> new SearchableFood(
                FoodNutritionSnapshot.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .category(StringUtils.hasText(rs.getString("category_name")) ? rs.getString("category_name") : rs.getString("category"))
                        .unit(rs.getString("unit"))
                        .calories(defaultValue(rs.getBigDecimal("calories")))
                        .protein(defaultValue(rs.getBigDecimal("protein")))
                        .fat(defaultValue(rs.getBigDecimal("fat")))
                        .carbohydrate(defaultValue(rs.getBigDecimal("carbohydrate")))
                        .fiber(defaultValue(rs.getBigDecimal("fiber")))
                        .status((Integer) rs.getObject("status"))
                        .build(),
                rs.getString("concept_name"),
                rs.getString("concept_name_en"),
                splitJoinedValues(rs.getString("food_aliases")),
                splitJoinedValues(rs.getString("concept_aliases"))
        ));

        List<ScoredFood> matched = foods.stream()
                .filter(item -> matchesCategory(item.snapshot().getCategory(), category))
                .map(item -> new ScoredFood(item.snapshot(), calculateScore(item, keyword)))
                .filter(item -> !StringUtils.hasText(keyword) || item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredFood::score).reversed()
                        .thenComparing(item -> safe(item.snapshot().getName())))
                .toList();

        long total = matched.size();
        long currentPage = Math.max(current, 1);
        long pageSize = Math.max(size, 1);
        int fromIndex = (int) Math.min((currentPage - 1) * pageSize, total);
        int toIndex = (int) Math.min(fromIndex + pageSize, total);

        List<FoodNutritionSnapshot> records = matched.subList(fromIndex, toIndex).stream()
                .map(ScoredFood::snapshot)
                .toList();

        return new PageResponse<>(records, total, currentPage, pageSize);
    }

    @Override
    public FoodNutritionSnapshot getFood(Long id) {
        FoodBasic food = requireFood(id);
        return toSnapshot(food);
    }

    @Override
    public FoodNutritionSnapshot createFood(FoodUpsertRequest request) {
        FoodBasic food = new FoodBasic();
        applyRequest(food, request);
        save(food);
        return toSnapshot(food);
    }

    @Override
    public FoodNutritionSnapshot updateFood(Long id, FoodUpsertRequest request) {
        FoodBasic food = requireFood(id);
        applyRequest(food, request);
        updateById(food);
        return toSnapshot(food);
    }

    @Override
    public void deleteFood(Long id) {
        removeById(id);
    }

    private boolean matchesCategory(String foodCategory, String category) {
        if (!StringUtils.hasText(category)) {
            return true;
        }
        String normalizedCategory = normalize(category);
        String normalizedFoodCategory = normalize(foodCategory);
        return normalizedFoodCategory.contains(normalizedCategory)
                || normalizedCategory.contains(normalizedFoodCategory);
    }

    private double calculateScore(SearchableFood food, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return 1.0;
        }

        String normalizedKeyword = normalize(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return 0.0;
        }

        double score = 0.0;
        score = Math.max(score, scoreText(normalizedKeyword, food.snapshot().getName(), 120, 90, 30));
        score = Math.max(score, scoreText(normalizedKeyword, food.snapshot().getCategory(), 45, 30, 10));
        score = Math.max(score, scoreText(normalizedKeyword, food.conceptName(), 95, 70, 24));
        score = Math.max(score, scoreText(normalizedKeyword, food.conceptNameEn(), 92, 68, 22));

        for (String alias : food.foodAliases()) {
            score = Math.max(score, scoreText(normalizedKeyword, alias, 110, 82, 26));
        }
        for (String alias : food.conceptAliases()) {
            score = Math.max(score, scoreText(normalizedKeyword, alias, 102, 76, 24));
        }

        int overlap = countTokenOverlap(keyword, food);
        if (overlap > 0) {
            score += overlap * 9.0;
        }

        return score;
    }

    private int countTokenOverlap(String keyword, SearchableFood food) {
        List<String> queryTokens = splitSearchTokens(keyword);
        if (queryTokens.isEmpty()) {
            return 0;
        }

        int overlap = 0;
        for (String token : queryTokens) {
            if (matchesAnyToken(token, food)) {
                overlap++;
            }
        }
        return overlap;
    }

    private boolean matchesAnyToken(String token, SearchableFood food) {
        if (containsText(token, food.snapshot().getName())
                || containsText(token, food.snapshot().getCategory())
                || containsText(token, food.conceptName())
                || containsText(token, food.conceptNameEn())) {
            return true;
        }

        for (String alias : food.foodAliases()) {
            if (containsText(token, alias)) {
                return true;
            }
        }
        for (String alias : food.conceptAliases()) {
            if (containsText(token, alias)) {
                return true;
            }
        }
        return false;
    }

    private double scoreText(String normalizedKeyword,
                             String rawValue,
                             double exactScore,
                             double containsScore,
                             double reverseContainsScore) {
        String normalizedValue = normalize(rawValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return 0.0;
        }
        if (normalizedValue.equals(normalizedKeyword)) {
            return exactScore;
        }
        if (normalizedValue.contains(normalizedKeyword)) {
            return containsScore;
        }
        if (normalizedKeyword.contains(normalizedValue)) {
            return reverseContainsScore;
        }
        return 0.0;
    }

    private boolean containsText(String query, String rawValue) {
        String normalizedValue = normalize(rawValue);
        return StringUtils.hasText(normalizedValue)
                && (normalizedValue.contains(query) || query.contains(normalizedValue));
    }

    private List<String> splitJoinedValues(String joined) {
        if (!StringUtils.hasText(joined)) {
            return List.of();
        }

        String[] parts = joined.split("\\|\\|");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
        return values.stream().distinct().toList();
    }

    private List<String> splitSearchTokens(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        return List.of(keyword.trim().split("[\\s,，/|]+")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private FoodBasic requireFood(Long id) {
        FoodBasic food = getById(id);
        if (food == null) {
            throw new IllegalArgumentException("food not found: " + id);
        }
        return food;
    }

    private void applyRequest(FoodBasic food, FoodUpsertRequest request) {
        food.setName(request.getName());
        food.setCategory(request.getCategory());
        food.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : "100g");
        food.setCalories(defaultValue(request.getCalories()));
        food.setProtein(defaultValue(request.getProtein()));
        food.setFat(defaultValue(request.getFat()));
        food.setCarbohydrate(defaultValue(request.getCarbohydrate()));
        food.setFiber(defaultValue(request.getFiber()));
        food.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private FoodNutritionSnapshot toSnapshot(FoodBasic food) {
        return FoodNutritionSnapshot.builder()
                .id(food.getId())
                .name(food.getName())
                .category(food.getCategory())
                .unit(food.getUnit())
                .calories(defaultValue(food.getCalories()))
                .protein(defaultValue(food.getProtein()))
                .fat(defaultValue(food.getFat()))
                .carbohydrate(defaultValue(food.getCarbohydrate()))
                .fiber(defaultValue(food.getFiber()))
                .status(food.getStatus())
                .build();
    }

    private record SearchableFood(FoodNutritionSnapshot snapshot,
                                  String conceptName,
                                  String conceptNameEn,
                                  List<String> foodAliases,
                                  List<String> conceptAliases) {
    }

    private record ScoredFood(FoodNutritionSnapshot snapshot, double score) {
    }
}
