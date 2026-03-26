package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MealPlanDaySummaryResponse {

    private LocalDate planDate;

    private String title;

    private String status;

    private Integer itemCount;

    private BigDecimal totalCalories;

    private Boolean hasPlan;
}
