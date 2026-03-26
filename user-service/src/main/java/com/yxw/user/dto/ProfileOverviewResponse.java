package com.yxw.user.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProfileOverviewResponse {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String role;

    private HealthProfileResponse healthProfile;

    private HealthGoalResponse healthGoal;

    private BigDecimal latestWeightKg;

    private List<WeightLogResponse> recentWeightLogs;
}
