package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MealPlanItemResponse {

    private Long id;

    private Long foodId;

    private String foodName;

    private String mealType;

    private BigDecimal quantity;

    private String note;

    private Integer sortOrder;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;
}
