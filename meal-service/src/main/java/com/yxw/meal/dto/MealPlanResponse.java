package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MealPlanResponse {

    private Long id;

    private LocalDate planDate;

    private String title;

    private String notes;

    private String status;

    private BigDecimal totalCalories;

    private BigDecimal totalProtein;

    private BigDecimal totalFat;

    private BigDecimal totalCarbohydrate;

    private List<MealPlanItemResponse> items;
}
