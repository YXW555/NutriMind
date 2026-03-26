package com.yxw.meal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthGoalSnapshot {

    private Long id;

    private String goalType;

    private BigDecimal targetCalories;

    private BigDecimal targetProtein;

    private BigDecimal targetFat;

    private BigDecimal targetCarbohydrate;

    private BigDecimal targetWeightKg;

    private BigDecimal weeklyChangeKg;

    private LocalDate startDate;

    private LocalDate endDate;

    private String note;
}
