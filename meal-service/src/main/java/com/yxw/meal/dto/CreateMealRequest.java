package com.yxw.meal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateMealRequest {

    private Long userId;

    @NotNull(message = "recordDate must not be null")
    private LocalDate recordDate;

    @Valid
    @NotEmpty(message = "details must not be empty")
    private List<MealDetailCommand> details;
}
