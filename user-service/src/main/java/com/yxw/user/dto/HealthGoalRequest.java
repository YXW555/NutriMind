package com.yxw.user.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HealthGoalRequest {

    private String goalType;

    @DecimalMin(value = "0.00", message = "targetCalories must be positive")
    private BigDecimal targetCalories;

    @DecimalMin(value = "0.00", message = "targetProtein must be positive")
    private BigDecimal targetProtein;

    @DecimalMin(value = "0.00", message = "targetFat must be positive")
    private BigDecimal targetFat;

    @DecimalMin(value = "0.00", message = "targetCarbohydrate must be positive")
    private BigDecimal targetCarbohydrate;

    @DecimalMin(value = "0.00", message = "targetWeightKg must be positive")
    private BigDecimal targetWeightKg;

    private BigDecimal weeklyChangeKg;

    private LocalDate startDate;

    private LocalDate endDate;

    private String note;
}
