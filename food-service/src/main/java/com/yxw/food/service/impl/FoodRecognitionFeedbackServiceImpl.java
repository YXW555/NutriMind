package com.yxw.food.service.impl;

import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.food.dto.FoodRecognitionFeedbackRequest;
import com.yxw.food.service.FoodRecognitionFeedbackService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FoodRecognitionFeedbackServiceImpl implements FoodRecognitionFeedbackService {

    private final JdbcTemplate jdbcTemplate;

    public FoodRecognitionFeedbackServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordFeedback(FoodRecognitionFeedbackRequest request) {
        if (request == null || request.getFoodId() == null) {
            return;
        }

        Long userId = SecurityContextUtils.currentUserId().orElse(null);
        String searchTerms = joinSearchTerms(request.getSearchTerms());

        jdbcTemplate.update("""
                INSERT INTO food_recognition_logs(
                    user_id,
                    food_id,
                    recognized_label,
                    recognized_canonical_label,
                    matched_food_name,
                    confidence,
                    recognition_mode,
                    search_terms,
                    manual_confirmation_required
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                request.getFoodId(),
                trimToNull(request.getRecognizedLabel()),
                trimToNull(request.getRecognizedCanonicalLabel()),
                trimToNull(request.getMatchedFoodName()),
                normalizeConfidence(request.getConfidence()),
                trimToNull(request.getRecognitionMode()),
                trimToNull(searchTerms),
                Boolean.TRUE.equals(request.getManualConfirmationRequired()) ? 1 : 0
        );
    }

    private BigDecimal normalizeConfidence(BigDecimal confidence) {
        return confidence == null ? null : confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private String joinSearchTerms(List<String> searchTerms) {
        if (searchTerms == null || searchTerms.isEmpty()) {
            return null;
        }
        return searchTerms.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
