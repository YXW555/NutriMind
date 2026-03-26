package com.yxw.common.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Shared food nutrition view model used across services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodNutritionSnapshot {

    private Long id;

    private String name;

    private String category;

    private String unit;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private BigDecimal fiber;

    private Integer status;
}
