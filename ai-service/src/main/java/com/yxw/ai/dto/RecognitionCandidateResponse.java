package com.yxw.ai.dto;

import com.yxw.common.core.dto.FoodNutritionSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    public static RecognitionCandidateResponse fromFood(FoodNutritionSnapshot food,
                                                        BigDecimal confidence,
                                                        String matchReason) {
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
                .build();
    }
}
