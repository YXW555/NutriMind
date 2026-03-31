package com.yxw.meal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MealPlanGenerateDailyRequest {

    @NotNull(message = "planDate must not be null")
    private LocalDate planDate;

    /**
     * User free text, e.g. "训练日" / "想吃清淡点" / "预算 30".
     */
    private String preference;

    /**
     * If true, persist as a draft plan and return the saved response (includes id/status).
     */
    private Boolean saveDraft;
}

