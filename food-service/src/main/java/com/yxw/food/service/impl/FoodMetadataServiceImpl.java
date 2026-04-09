package com.yxw.food.service.impl;

import com.yxw.food.dto.FoodCategoryResponse;
import com.yxw.food.dto.FoodImageSampleResponse;
import com.yxw.food.dto.FoodMetadataResponse;
import com.yxw.food.dto.FoodRecognitionLogResponse;
import com.yxw.food.service.FoodMetadataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class FoodMetadataServiceImpl implements FoodMetadataService {

    private final JdbcTemplate jdbcTemplate;

    public FoodMetadataServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FoodCategoryResponse> listCategories() {
        return jdbcTemplate.query("""
                SELECT id, name, parent_id, description, sort_order, status
                FROM food_categories
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> FoodCategoryResponse.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .parentId((Long) rs.getObject("parent_id"))
                .description(rs.getString("description"))
                .sortOrder((Integer) rs.getObject("sort_order"))
                .status((Integer) rs.getObject("status"))
                .build());
    }

    @Override
    public FoodMetadataResponse getFoodMetadata(Long foodId) {
        List<FoodMetadataResponse> rows = jdbcTemplate.query("""
                SELECT b.id AS food_id,
                       b.category_id,
                       b.concept_id,
                       c.name AS category_name,
                       fc.concept_code,
                       fc.canonical_name AS concept_name,
                       fc.canonical_name_en AS concept_name_en
                FROM food_basics b
                LEFT JOIN food_categories c ON b.category_id = c.id
                LEFT JOIN food_concepts fc ON b.concept_id = fc.id
                WHERE b.id = ?
                LIMIT 1
                """, (rs, rowNum) -> FoodMetadataResponse.builder()
                .foodId(rs.getLong("food_id"))
                .categoryId((Long) rs.getObject("category_id"))
                .categoryName(rs.getString("category_name"))
                .conceptId((Long) rs.getObject("concept_id"))
                .conceptCode(rs.getString("concept_code"))
                .conceptName(rs.getString("concept_name"))
                .conceptNameEn(rs.getString("concept_name_en"))
                .build(), foodId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("food not found: " + foodId);
        }

        FoodMetadataResponse base = rows.get(0);
        List<String> aliases = jdbcTemplate.query("""
                SELECT alias_name
                FROM food_aliases
                WHERE food_id = ?
                ORDER BY alias_type ASC, alias_name ASC
                """, (rs, rowNum) -> rs.getString("alias_name"), foodId);

        List<FoodImageSampleResponse> imageSamples = jdbcTemplate.query("""
                SELECT id, image_url, source, description, sort_order
                FROM food_image_samples
                WHERE food_id = ?
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> FoodImageSampleResponse.builder()
                .id(rs.getLong("id"))
                .imageUrl(rs.getString("image_url"))
                .source(rs.getString("source"))
                .description(rs.getString("description"))
                .sortOrder((Integer) rs.getObject("sort_order"))
                .build(), foodId);

        List<String> conceptAliases = base.getConceptId() == null
                ? Collections.emptyList()
                : jdbcTemplate.query("""
                SELECT alias_name
                FROM food_concept_aliases
                WHERE concept_id = ?
                ORDER BY alias_type ASC, alias_name ASC
                """, (rs, rowNum) -> rs.getString("alias_name"), base.getConceptId());

        base.setAliases(aliases == null ? Collections.emptyList() : aliases);
        base.setConceptAliases(conceptAliases == null ? Collections.emptyList() : conceptAliases);
        base.setImageSamples(imageSamples == null ? Collections.emptyList() : imageSamples);
        return base;
    }

    @Override
    public List<FoodRecognitionLogResponse> listRecognitionLogs(Long foodId, int size) {
        int limit = Math.max(1, Math.min(size, 100));
        String sql = """
                SELECT id,
                       user_id,
                       food_id,
                       matched_food_name,
                       recognized_label,
                       recognized_canonical_label,
                       confidence,
                       recognition_mode,
                       search_terms,
                       manual_confirmation_required,
                       created_at
                FROM food_recognition_logs
                WHERE (? IS NULL OR food_id = ?)
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> FoodRecognitionLogResponse.builder()
                        .id(rs.getLong("id"))
                        .userId((Long) rs.getObject("user_id"))
                        .foodId((Long) rs.getObject("food_id"))
                        .matchedFoodName(rs.getString("matched_food_name"))
                        .recognizedLabel(rs.getString("recognized_label"))
                        .recognizedCanonicalLabel(rs.getString("recognized_canonical_label"))
                        .confidence(rs.getBigDecimal("confidence"))
                        .recognitionMode(rs.getString("recognition_mode"))
                        .searchTerms(rs.getString("search_terms"))
                        .manualConfirmationRequired(((Integer) rs.getObject("manual_confirmation_required")) == null
                                ? null
                                : ((Integer) rs.getObject("manual_confirmation_required")) == 1)
                        .createdAt(rs.getTimestamp("created_at") == null
                                ? null
                                : rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                foodId, foodId, limit);
    }
}
