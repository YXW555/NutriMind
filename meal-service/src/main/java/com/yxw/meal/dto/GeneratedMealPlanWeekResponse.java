package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class GeneratedMealPlanWeekResponse {

    private LocalDate weekStart;

    private String generationMode;

    private String summary;

    private List<String> tips;

    private List<GeneratedMealPlanResponse> days;
}
