package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class GeneratedMealPlanResponse {

    private LocalDate planDate;

    private String title;

    private String notes;

    private String generationMode;

    private String summary;

    private BigDecimal targetCalories;

    private BigDecimal targetProtein;

    private BigDecimal totalCalories;

    private BigDecimal totalProtein;

    private BigDecimal totalFat;

    private BigDecimal totalCarbohydrate;

    private BigDecimal calorieGap;

    private BigDecimal proteinGap;

    private List<String> tips;

    private List<String> warnings;

    private List<String> references;

    private List<MealPlanItemResponse> items;
}
