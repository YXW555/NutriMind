package com.yxw.ai.dto;

import com.yxw.common.core.dto.FoodNutritionSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionCandidateResponse {

    private Long id;

    private String name;

    private String category;

    private String unit;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private BigDecimal confidence;

    private String matchReason;

    private Boolean manualConfirmationRequired;

    private Boolean estimated;

    private String estimateSourceSummary;

    private String recognizedLabel;

    private String recognizedCanonicalLabel;

    private List<String> searchKeywords;

    public static RecognitionCandidateResponse fromFood(FoodNutritionSnapshot food,
                                                        BigDecimal confidence,
                                                        String matchReason,
                                                        String recognizedLabel,
                                                        String recognizedCanonicalLabel,
                                                        List<String> searchKeywords) {
        return RecognitionCandidateResponse.builder()
                .id(food.getId())
                .name(food.getName())
                .category(food.getCategory())
                .unit(food.getUnit())
                .calories(food.getCalories())
                .protein(food.getProtein())
                .fat(food.getFat())
                .carbohydrate(food.getCarbohydrate())
                .confidence(confidence)
                .matchReason(matchReason)
                .manualConfirmationRequired(Boolean.TRUE)
                .estimated(Boolean.FALSE)
                .recognizedLabel(recognizedLabel)
                .recognizedCanonicalLabel(recognizedCanonicalLabel)
                .searchKeywords(searchKeywords == null ? List.of() : searchKeywords)
                .build();
    }
}
