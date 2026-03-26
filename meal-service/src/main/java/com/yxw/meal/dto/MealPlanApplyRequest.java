package com.yxw.meal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MealPlanApplyRequest {

    @NotNull(message = "planDate must not be null")
    private LocalDate planDate;
}
