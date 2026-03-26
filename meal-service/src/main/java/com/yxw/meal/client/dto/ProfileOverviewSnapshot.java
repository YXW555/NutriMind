package com.yxw.meal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileOverviewSnapshot {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String role;

    private HealthProfileSnapshot healthProfile;

    private HealthGoalSnapshot healthGoal;

    private BigDecimal latestWeightKg;

    private List<WeightLogSnapshot> recentWeightLogs;
}
