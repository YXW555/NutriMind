package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RewardFeedbackResponse {

    private Integer pointsEarned;

    private Integer totalPoints;

    private Integer badgeCount;

    private Integer currentStreak;

    private List<String> messages;
}
