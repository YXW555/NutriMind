package com.yxw.meal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MealPlanGenerateWeekRequest {

    /**
     * Any date within the target week. The generated plan will start from Monday.
     */
    @NotNull(message = "anchorDate must not be null")
    private LocalDate anchorDate;

    private String preference;

    private Boolean saveDraft;
}

