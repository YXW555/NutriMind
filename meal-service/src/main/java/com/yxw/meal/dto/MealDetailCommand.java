package com.yxw.meal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MealDetailCommand {

    @NotNull(message = "食物ID不能为空")
    private Long foodId;

    @NotNull(message = "食用量不能为空")
    @DecimalMin(value = "0.01", message = "食用量必须大于0")
    private BigDecimal quantity;

    private String mealType;
}
