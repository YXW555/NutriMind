package com.yxw.meal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MealPlanSaveRequest {

    @NotNull(message = "planDate must not be null")
    private LocalDate planDate;

    private String title;

    private String notes;

    @Valid
    private List<MealPlanItemCommand> items;
}
