package com.yxw.food.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.bootstrap.food-metadata-enabled", havingValue = "true", matchIfMissing = true)
public class FoodCatalogMetadataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodCatalogMetadataBootstrap.class);

    private static final Map<String, String> GROUP_TO_CATEGORY = Map.ofEntries(
            Map.entry("staple", "主食"),
            Map.entry("protein", "蛋白质"),
            Map.entry("vegetable", "蔬菜"),
            Map.entry("fruit", "水果"),
            Map.entry("beverage", "饮品"),
            Map.entry("light_meal", "轻食"),
            Map.entry("compound", "复合餐"),
            Map.entry("dish", "家常菜"),
            Map.entry("takeout", "外卖")
    );

    private static final Map<String, List<String>> EXTRA_CONCEPT_ALIASES = Map.ofEntries(
            Map.entry("chicken_breast", List.of("white meat", "chicken", "poultry", "chicken meat", "chicken leg", "drumstick", "chicken wing", "roast chicken", "fried chicken")),
            Map.entry("beef", List.of("red meat", "beef", "steak", "beef slices", "lean beef")),
            Map.entry("fish", List.of("fish", "seafood", "white fish", "salmon", "cod", "tilapia")),
            Map.entry("shrimp", List.of("shrimp", "prawn", "seafood")),
            Map.entry("salad", List.of("salad", "vegetable salad", "chicken salad", "light meal")),
            Map.entry("sandwich", List.of("sandwich", "toast sandwich")),
            Map.entry("fried_rice", List.of("fried rice", "rice dish", "rice meal")),
            Map.entry("fried_noodles", List.of("fried noodles", "noodle dish")),
            Map.entry("stir_fried_vegetables", List.of("mixed vegetables", "vegetable dish", "stir fried vegetables")),
            Map.entry("tomato_egg", List.of("tomato and egg", "scrambled egg with tomato")),
            Map.entry("kung_pao_chicken", List.of("kung pao chicken", "chicken cubes")),
            Map.entry("tomato", List.of("tomato", "white meat side dish")),
            Map.entry("broccoli", List.of("broccoli", "green vegetable")),
            Map.entry("cucumber", List.of("cucumber", "green vegetable")),
            Map.entry("banana", List.of("banana", "fruit")),
            Map.entry("apple", List.of("apple", "fruit")),
            Map.entry("orange", List.of("orange", "fruit")),
            Map.entry("milk", List.of("milk", "dairy")),
            Map.entry("yogurt", List.of("yogurt", "dairy")),
            Map.entry("rice", List.of("rice", "white rice", "steamed rice")),
            Map.entry("noodles", List.of("noodles", "soup noodles"))
    );

    private static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("主食", null, "米饭、面条、面包等主食类", 10),
            new CategorySeed("蛋白质", null, "肉类、鸡蛋、奶制品、豆制品和海鲜", 20),
            new CategorySeed("蔬菜", null, "蔬菜和菌菇类", 30),
            new CategorySeed("水果", null, "新鲜水果", 40),
            new CategorySeed("饮品", null, "牛奶、茶饮和果汁等", 50),
            new CategorySeed("轻食", null, "沙拉、三明治等轻食", 60),
            new CategorySeed("复合餐", null, "炒饭、炒面、套餐等组合餐", 70),
            new CategorySeed("家常菜", null, "常见家常菜", 80),
            new CategorySeed("外卖", null, "外卖菜品和套餐", 90)
    );

    private static final List<AliasSeed> FOOD_ALIAS_SEEDS = List.of(
            new AliasSeed("米饭", "白米饭", "COMMON"),
            new AliasSeed("米饭", "白饭", "SEARCH"),
            new AliasSeed("燕麦片", "燕麦", "COMMON"),
            new AliasSeed("西兰花", "西蓝花", "COMMON"),
            new AliasSeed("番茄", "西红柿", "COMMON"),
            new AliasSeed("土豆", "马铃薯", "COMMON"),
            new AliasSeed("牛奶", "纯牛奶", "COMMON"),
            new AliasSeed("酸奶", "优酪乳", "SEARCH"),
            new AliasSeed("鸡胸肉", "鸡胸", "COMMON"),
            new AliasSeed("鸡胸肉", "鸡肉", "VISION"),
            new AliasSeed("三文鱼", "三文鱼排", "SEARCH"),
            new AliasSeed("豆腐", "嫩豆腐", "COMMON"),
            new AliasSeed("北豆腐", "老豆腐", "COMMON"),
            new AliasSeed("香蕉", "香蕉片", "SEARCH"),
            new AliasSeed("橙子", "橙", "SEARCH"),
            new AliasSeed("沙拉", "蔬菜沙拉", "COMMON"),
            new AliasSeed("鸡肉沙拉", "鸡胸肉沙拉", "VISION"),
            new AliasSeed("三明治", "夹心三明治", "COMMON"),
            new AliasSeed("炒饭", "蛋炒饭", "VISION"),
            new AliasSeed("炒面", "拌炒面", "SEARCH"),
            new AliasSeed("西红柿炒蛋", "番茄炒蛋", "COMMON"),
            new AliasSeed("宫保鸡丁", "宫爆鸡丁", "COMMON"),
            new AliasSeed("清炒时蔬", "清炒蔬菜", "SEARCH"),
            new AliasSeed("鸡腿肉", "鸡腿", "COMMON"),
            new AliasSeed("鸡腿肉", "鸡小腿", "SEARCH"),
            new AliasSeed("鸡腿肉", "烤鸡腿", "VISION"),
            new AliasSeed("鸡腿肉", "炸鸡腿", "VISION"),
            new AliasSeed("鸡肉", "白肉", "VISION"),
            new AliasSeed("牛肉", "红肉", "VISION"),
            new AliasSeed("虾仁", "虾", "COMMON"),
            new AliasSeed("鱼肉", "鱼", "COMMON")
    );

    private static final List<ImageSeed> IMAGE_SAMPLE_SEEDS = List.of(
            new ImageSeed("米饭", "https://images.example.com/foods/rice-bowl.jpg", "Rice bowl sample"),
            new ImageSeed("鸡胸肉", "https://images.example.com/foods/chicken-breast.jpg", "Chicken breast sample"),
            new ImageSeed("鸡腿肉", "https://images.example.com/foods/chicken-leg.jpg", "Chicken leg sample"),
            new ImageSeed("西兰花", "https://images.example.com/foods/broccoli.jpg", "Broccoli sample"),
            new ImageSeed("鸡蛋", "https://images.example.com/foods/boiled-egg.jpg", "Egg sample"),
            new ImageSeed("牛奶", "https://images.example.com/foods/milk.jpg", "Milk sample"),
            new ImageSeed("香蕉", "https://images.example.com/foods/banana.jpg", "Banana sample"),
            new ImageSeed("鸡肉沙拉", "https://images.example.com/foods/chicken-salad.jpg", "Chicken salad sample"),
            new ImageSeed("西红柿炒蛋", "https://images.example.com/foods/tomato-egg.jpg", "Tomato and egg sample"),
            new ImageSeed("宫保鸡丁", "https://images.example.com/foods/kung-pao-chicken.jpg", "Kung pao chicken sample"),
            new ImageSeed("黄焖鸡米饭", "https://images.example.com/foods/braised-chicken-rice.jpg", "Takeout combo sample")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String conceptBankPath;

    public FoodCatalogMetadataBootstrap(JdbcTemplate jdbcTemplate,
                                        ObjectMapper objectMapper,
                                        @Value("${app.bootstrap.food-concept-bank-path:}") String conceptBankPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.conceptBankPath = conceptBankPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsertCategories();
        upsertConcepts();
        backfillFoodCategoryIds();
        backfillFoodConceptIds();
        upsertAliases();
        upsertImageSamples();
        log.info("Food metadata bootstrap completed.");
    }

    private void upsertCategories() {
        for (CategorySeed category : CATEGORY_SEEDS) {
            Long parentId = category.parentName() == null ? null : findCategoryId(category.parentName());
            jdbcTemplate.update("""
                    INSERT INTO food_categories(name, parent_id, description, sort_order, status)
                    VALUES (?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                    parent_id = VALUES(parent_id),
                    description = VALUES(description),
                    sort_order = VALUES(sort_order),
                    status = VALUES(status)
                    """, category.name(), parentId, category.description(), category.sortOrder());
        }
    }

    private void upsertConcepts() {
        List<ConceptSeed> conceptSeeds = loadConceptSeeds();
        for (ConceptSeed concept : conceptSeeds) {
            Long categoryId = resolveCategoryIdForConcept(concept);
            Long parentId = concept.parentCode() == null ? null : findConceptIdByCode(concept.parentCode());

            jdbcTemplate.update("""
                    INSERT INTO food_concepts(concept_code, canonical_name, canonical_name_en, parent_id, category_id,
                                              concept_level, description, sort_order, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                    canonical_name = VALUES(canonical_name),
                    canonical_name_en = VALUES(canonical_name_en),
                    parent_id = VALUES(parent_id),
                    category_id = VALUES(category_id),
                    concept_level = VALUES(concept_level),
                    description = VALUES(description),
                    sort_order = VALUES(sort_order),
                    status = VALUES(status)
                    """,
                    concept.conceptCode(),
                    concept.canonicalName(),
                    concept.canonicalNameEn(),
                    parentId,
                    categoryId,
                    concept.level(),
                    concept.description(),
                    concept.sortOrder());

            Long conceptId = findConceptIdByCode(concept.conceptCode());
            if (conceptId == null) {
                continue;
            }

            for (ConceptAliasSeed alias : concept.aliases()) {
                jdbcTemplate.update("""
                        INSERT INTO food_concept_aliases(concept_id, alias_name, alias_lang, alias_type)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                        alias_lang = VALUES(alias_lang),
                        alias_type = VALUES(alias_type)
                        """, conceptId, alias.aliasName(), alias.aliasLang(), alias.aliasType());
            }
        }
    }

    private void backfillFoodCategoryIds() {
        List<Map<String, Object>> foods = jdbcTemplate.queryForList("""
                SELECT id, category
                FROM food_basics
                WHERE category IS NOT NULL
                """);

        for (Map<String, Object> food : foods) {
            Long categoryId = findCategoryId(String.valueOf(food.get("category")));
            if (categoryId == null) {
                continue;
            }

            jdbcTemplate.update("""
                    UPDATE food_basics
                    SET category_id = ?
                    WHERE id = ? AND (category_id IS NULL OR category_id <> ?)
                    """, categoryId, food.get("id"), categoryId);
        }
    }

    private void backfillFoodConceptIds() {
        List<Map<String, Object>> foods = jdbcTemplate.queryForList("""
                SELECT id, name, category, concept_id
                FROM food_basics
                """);
        List<ConceptCandidate> concepts = loadConceptCandidates();

        for (Map<String, Object> food : foods) {
            if (food.get("concept_id") != null) {
                continue;
            }

            String foodName = String.valueOf(food.get("name"));
            String category = food.get("category") == null ? "" : String.valueOf(food.get("category"));
            Long conceptId = matchFoodConceptId(foodName, category, concepts);
            if (conceptId == null) {
                continue;
            }

            jdbcTemplate.update("""
                    UPDATE food_basics
                    SET concept_id = ?
                    WHERE id = ?
                    """, conceptId, food.get("id"));
        }
    }

    private void upsertAliases() {
        for (AliasSeed alias : FOOD_ALIAS_SEEDS) {
            Long foodId = findFoodId(alias.foodName());
            if (foodId == null) {
                continue;
            }

            jdbcTemplate.update("""
                    INSERT INTO food_aliases(food_id, alias_name, alias_type)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    alias_type = VALUES(alias_type)
                    """, foodId, alias.alias(), alias.aliasType());
        }
    }

    private void upsertImageSamples() {
        for (int i = 0; i < IMAGE_SAMPLE_SEEDS.size(); i++) {
            ImageSeed sample = IMAGE_SAMPLE_SEEDS.get(i);
            Long foodId = findFoodId(sample.foodName());
            if (foodId == null) {
                continue;
            }

            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM food_image_samples
                    WHERE food_id = ? AND image_url = ?
                    """, Integer.class, foodId, sample.imageUrl());
            if (count != null && count > 0) {
                continue;
            }

            jdbcTemplate.update("""
                    INSERT INTO food_image_samples(food_id, image_url, source, description, sort_order)
                    VALUES (?, ?, 'SYSTEM', ?, ?)
                    """, foodId, sample.imageUrl(), sample.description(), i);
        }
    }

    private List<ConceptSeed> loadConceptSeeds() {
        Optional<Path> bankPath = resolveConceptBankPath();
        if (bankPath.isEmpty()) {
            log.warn("Concept bank not found, using built-in fallback concept seeds.");
            return fallbackConceptSeeds();
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(bankPath.get()));
            JsonNode conceptsNode = root.path("concepts");
            if (!conceptsNode.isArray() || conceptsNode.isEmpty()) {
                log.warn("Concept bank {} has no concepts, using fallback concept seeds.", bankPath.get());
                return fallbackConceptSeeds();
            }

            List<ConceptSeed> concepts = new ArrayList<>();
            for (JsonNode node : conceptsNode) {
                String conceptCode = clean(node.path("concept_id").asText());
                if (!StringUtils.hasText(conceptCode)) {
                    continue;
                }

                String canonicalName = clean(node.path("canonical_label").asText());
                String englishName = clean(node.path("english_name").asText());
                String group = clean(node.path("group").asText());
                String groupZh = clean(node.path("group_zh").asText());
                int priority = node.path("priority").asInt(999);
                String notes = clean(node.path("notes").asText());

                List<ConceptAliasSeed> aliases = new ArrayList<>();
                collectConceptAliases(aliases, canonicalName, "zh", "COMMON");
                collectConceptAliases(aliases, englishName, "en", "COMMON");
                addAliasesFromArray(aliases, node.path("aliases"), "zh", "COMMON");
                addAliasesFromArray(aliases, node.path("search_keywords"), "mixed", "SEARCH");
                addAliasesFromArray(aliases, node.path("clip_prompts_zh"), "zh", "VISION");
                addAliasesFromArray(aliases, node.path("clip_prompts_en"), "en", "VISION");

                for (String extraAlias : EXTRA_CONCEPT_ALIASES.getOrDefault(conceptCode, List.of())) {
                    String lang = containsAsciiWord(extraAlias) ? "en" : "mixed";
                    collectConceptAliases(aliases, extraAlias, lang, "MODEL");
                }

                concepts.add(new ConceptSeed(
                        conceptCode,
                        canonicalName,
                        englishName,
                        null,
                        StringUtils.hasText(group) ? group : null,
                        StringUtils.hasText(groupZh) ? groupZh : mapGroupZh(group),
                        1,
                        StringUtils.hasText(notes) ? notes : buildDescription(canonicalName, englishName),
                        priority,
                        dedupeAliases(aliases)
                ));
            }

            return concepts.isEmpty() ? fallbackConceptSeeds() : concepts;
        } catch (IOException ex) {
            log.warn("Failed to parse concept bank, using built-in fallback concept seeds.", ex);
            return fallbackConceptSeeds();
        }
    }

    private void collectConceptAliases(List<ConceptAliasSeed> aliases, String value, String lang, String type) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        aliases.add(new ConceptAliasSeed(value.trim(), lang, type));
    }

    private void addAliasesFromArray(List<ConceptAliasSeed> aliases, JsonNode arrayNode, String lang, String type) {
        if (!arrayNode.isArray()) {
            return;
        }
        for (JsonNode item : arrayNode) {
            collectConceptAliases(aliases, clean(item.asText()), lang, type);
        }
    }

    private List<ConceptAliasSeed> dedupeAliases(List<ConceptAliasSeed> aliases) {
        LinkedHashMap<String, ConceptAliasSeed> deduped = new LinkedHashMap<>();
        for (ConceptAliasSeed alias : aliases) {
            String normalized = normalize(alias.aliasName());
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            deduped.putIfAbsent(normalized, alias);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<ConceptSeed> fallbackConceptSeeds() {
        return List.of(
                new ConceptSeed("rice", "米饭", "rice", null, "staple", "主食", 1, "基础主食概念", 1,
                        List.of(new ConceptAliasSeed("白米饭", "zh", "COMMON"), new ConceptAliasSeed("white rice", "en", "COMMON"))),
                new ConceptSeed("noodles", "面条", "noodles", null, "staple", "主食", 1, "基础面食概念", 2,
                        List.of(new ConceptAliasSeed("汤面", "zh", "COMMON"), new ConceptAliasSeed("soup noodles", "en", "COMMON"))),
                new ConceptSeed("egg", "鸡蛋", "egg", null, "protein", "蛋白质", 1, "鸡蛋概念", 3,
                        List.of(new ConceptAliasSeed("蛋", "zh", "COMMON"), new ConceptAliasSeed("boiled egg", "en", "COMMON"))),
                new ConceptSeed("milk", "牛奶", "milk", null, "beverage", "饮品", 1, "牛奶概念", 4,
                        List.of(new ConceptAliasSeed("纯牛奶", "zh", "COMMON"))),
                new ConceptSeed("banana", "香蕉", "banana", null, "fruit", "水果", 1, "香蕉概念", 5,
                        List.of(new ConceptAliasSeed("fruit", "en", "SEARCH"))),
                new ConceptSeed("apple", "苹果", "apple", null, "fruit", "水果", 1, "苹果概念", 6, List.of()),
                new ConceptSeed("broccoli", "西兰花", "broccoli", null, "vegetable", "蔬菜", 1, "西兰花概念", 7, List.of()),
                new ConceptSeed("tomato", "番茄", "tomato", null, "vegetable", "蔬菜", 1, "番茄概念", 8,
                        List.of(new ConceptAliasSeed("西红柿", "zh", "COMMON"))),
                new ConceptSeed("potato", "土豆", "potato", null, "vegetable", "蔬菜", 1, "土豆概念", 9,
                        List.of(new ConceptAliasSeed("马铃薯", "zh", "COMMON"))),
                new ConceptSeed("tofu", "豆腐", "tofu", null, "protein", "蛋白质", 1, "豆腐概念", 10, List.of()),
                new ConceptSeed("chicken_breast", "鸡肉", "chicken", null, "protein", "蛋白质", 1, "鸡肉概念", 11,
                        List.of(new ConceptAliasSeed("white meat", "en", "MODEL"), new ConceptAliasSeed("chicken leg", "en", "MODEL"))),
                new ConceptSeed("beef", "牛肉", "beef", null, "protein", "蛋白质", 1, "牛肉概念", 12,
                        List.of(new ConceptAliasSeed("red meat", "en", "MODEL"))),
                new ConceptSeed("fish", "鱼肉", "fish", null, "protein", "蛋白质", 1, "鱼肉概念", 13,
                        List.of(new ConceptAliasSeed("seafood", "en", "MODEL"))),
                new ConceptSeed("shrimp", "虾仁", "shrimp", null, "protein", "蛋白质", 1, "虾类概念", 14, List.of()),
                new ConceptSeed("salad", "沙拉", "salad", null, "light_meal", "轻食", 1, "沙拉概念", 15, List.of()),
                new ConceptSeed("sandwich", "三明治", "sandwich", null, "light_meal", "轻食", 1, "三明治概念", 16, List.of()),
                new ConceptSeed("fried_rice", "炒饭", "fried rice", null, "compound", "复合餐", 1, "炒饭概念", 17, List.of()),
                new ConceptSeed("fried_noodles", "炒面", "fried noodles", null, "compound", "复合餐", 1, "炒面概念", 18, List.of()),
                new ConceptSeed("tomato_egg", "西红柿炒蛋", "tomato egg", null, "dish", "家常菜", 1, "西红柿炒蛋概念", 19, List.of()),
                new ConceptSeed("kung_pao_chicken", "宫保鸡丁", "kung pao chicken", null, "dish", "家常菜", 1, "宫保鸡丁概念", 20, List.of()),
                new ConceptSeed("stir_fried_vegetables", "清炒时蔬", "stir fried vegetables", null, "dish", "家常菜", 1, "清炒时蔬概念", 21, List.of())
        );
    }

    private Long resolveCategoryIdForConcept(ConceptSeed concept) {
        String categoryName = StringUtils.hasText(concept.groupZh()) ? concept.groupZh() : mapGroupZh(concept.group());
        return findCategoryId(categoryName);
    }

    private String mapGroupZh(String group) {
        if (!StringUtils.hasText(group)) {
            return null;
        }
        return GROUP_TO_CATEGORY.get(group.trim().toLowerCase(Locale.ROOT));
    }

    private Optional<Path> resolveConceptBankPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(conceptBankPath)) {
            candidates.add(Paths.get(conceptBankPath.trim()));
        }
        candidates.add(Paths.get("ai-service", "model", "retrieval_bank.json"));
        candidates.add(Paths.get("model", "retrieval_bank.json"));
        candidates.add(Paths.get("..", "ai-service", "model", "retrieval_bank.json"));

        return candidates.stream()
                .filter(Files::exists)
                .findFirst();
    }

    private List<ConceptCandidate> loadConceptCandidates() {
        return jdbcTemplate.query("""
                SELECT c.id,
                       c.concept_code,
                       c.canonical_name,
                       c.canonical_name_en,
                       c.category_id,
                       GROUP_CONCAT(DISTINCT cca.alias_name SEPARATOR '||') AS aliases
                FROM food_concepts c
                LEFT JOIN food_concept_aliases cca ON cca.concept_id = c.id
                WHERE c.status = 1
                GROUP BY c.id, c.concept_code, c.canonical_name, c.canonical_name_en, c.category_id
                ORDER BY c.sort_order ASC, c.id ASC
                """, (rs, rowNum) -> {
                    List<String> aliases = splitJoinedValues(rs.getString("aliases"));
                    return new ConceptCandidate(
                            rs.getLong("id"),
                            rs.getString("concept_code"),
                            rs.getString("canonical_name"),
                            rs.getString("canonical_name_en"),
                            (Long) rs.getObject("category_id"),
                            aliases
                    );
                });
    }

    private Long matchFoodConceptId(String foodName, String category, List<ConceptCandidate> concepts) {
        String normalizedFoodName = normalize(foodName);
        if (!StringUtils.hasText(normalizedFoodName)) {
            return null;
        }

        Long categoryId = findCategoryId(category);
        ConceptCandidate bestConcept = null;
        int bestScore = 0;

        for (ConceptCandidate concept : concepts) {
            int score = 0;
            score = Math.max(score, scoreTextMatch(normalizedFoodName, concept.canonicalName()));
            score = Math.max(score, scoreTextMatch(normalizedFoodName, concept.canonicalNameEn()));

            for (String alias : concept.aliases()) {
                score = Math.max(score, scoreTextMatch(normalizedFoodName, alias));
            }

            if (categoryId != null && categoryId.equals(concept.categoryId())) {
                score += 8;
            }

            if (score > bestScore) {
                bestScore = score;
                bestConcept = concept;
            }
        }

        return bestScore >= 24 && bestConcept != null ? bestConcept.id() : null;
    }

    private int scoreTextMatch(String normalizedFoodName, String value) {
        String normalizedValue = normalize(value);
        if (!StringUtils.hasText(normalizedValue)) {
            return 0;
        }
        if (normalizedFoodName.equals(normalizedValue)) {
            return 40;
        }
        if (normalizedFoodName.contains(normalizedValue) || normalizedValue.contains(normalizedFoodName)) {
            return Math.min(34, 20 + Math.min(normalizedValue.length(), normalizedFoodName.length()));
        }
        return 0;
    }

    private Long findCategoryId(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM food_categories
                WHERE name = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), name.trim());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findConceptIdByCode(String conceptCode) {
        if (!StringUtils.hasText(conceptCode)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM food_concepts
                WHERE concept_code = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), conceptCode.trim());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findFoodId(String name) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM food_basics
                WHERE name = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<String> splitJoinedValues(String joined) {
        if (!StringUtils.hasText(joined)) {
            return List.of();
        }
        return List.of(joined.split("\\|\\|")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private String buildDescription(String canonicalName, String englishName) {
        if (StringUtils.hasText(canonicalName) && StringUtils.hasText(englishName)) {
            return canonicalName + " / " + englishName;
        }
        return StringUtils.hasText(canonicalName) ? canonicalName : englishName;
    }

    private boolean containsAsciiWord(String value) {
        return value != null && value.matches(".*[A-Za-z].*");
    }

    private record CategorySeed(String name, String parentName, String description, int sortOrder) {
    }

    private record AliasSeed(String foodName, String alias, String aliasType) {
    }

    private record ImageSeed(String foodName, String imageUrl, String description) {
    }

    private record ConceptSeed(String conceptCode,
                               String canonicalName,
                               String canonicalNameEn,
                               String parentCode,
                               String group,
                               String groupZh,
                               int level,
                               String description,
                               int sortOrder,
                               List<ConceptAliasSeed> aliases) {
    }

    private record ConceptAliasSeed(String aliasName, String aliasLang, String aliasType) {
    }

    private record ConceptCandidate(Long id,
                                    String conceptCode,
                                    String canonicalName,
                                    String canonicalNameEn,
                                    Long categoryId,
                                    List<String> aliases) {
    }
}
