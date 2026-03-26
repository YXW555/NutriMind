package com.yxw.meal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MealPlanItemCommand {

    @NotNull(message = "foodId must not be null")
    private Long foodId;

    private String mealType;

    @NotNull(message = "quantity must not be null")
    @DecimalMin(value = "0.01", message = "quantity must be greater than zero")
    private BigDecimal quantity;

    private String note;

    private Integer sortOrder;
}
