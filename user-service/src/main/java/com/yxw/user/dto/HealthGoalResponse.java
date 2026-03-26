package com.yxw.user.dto;

import com.yxw.user.entity.HealthGoal;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class HealthGoalResponse {

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

    public static HealthGoalResponse from(HealthGoal goal) {
        if (goal == null) {
            return HealthGoalResponse.builder().build();
        }
        return HealthGoalResponse.builder()
                .id(goal.getId())
                .goalType(goal.getGoalType())
                .targetCalories(goal.getTargetCalories())
                .targetProtein(goal.getTargetProtein())
                .targetFat(goal.getTargetFat())
                .targetCarbohydrate(goal.getTargetCarbohydrate())
                .targetWeightKg(goal.getTargetWeightKg())
                .weeklyChangeKg(goal.getWeeklyChangeKg())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .note(goal.getNote())
                .build();
    }
}
