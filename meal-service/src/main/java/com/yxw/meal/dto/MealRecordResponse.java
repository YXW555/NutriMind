package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealRecordResponse {

    private Long id;

    private Long userId;

    private LocalDate recordDate;

    private BigDecimal totalCalories;

    private BigDecimal totalProtein;

    private BigDecimal totalFat;

    private BigDecimal totalCarbohydrate;

    private List<MealDetailResponse> details;
}
