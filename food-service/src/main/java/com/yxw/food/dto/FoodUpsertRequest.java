package com.yxw.food.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FoodUpsertRequest {

    @NotBlank(message = "食物名称不能为空")
    private String name;

    private String category;

    private Long ownerUserId;

    private String sourceType;

    private String unit = "100g";

    @DecimalMin(value = "0.0", message = "热量不能小于0")
    private BigDecimal calories = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "蛋白质不能小于0")
    private BigDecimal protein = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "脂肪不能小于0")
    private BigDecimal fat = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "碳水不能小于0")
    private BigDecimal carbohydrate = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "膳食纤维不能小于0")
    private BigDecimal fiber = BigDecimal.ZERO;

    private Integer status = 1;
}
