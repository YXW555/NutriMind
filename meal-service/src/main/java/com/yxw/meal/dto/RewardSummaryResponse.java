package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RewardSummaryResponse {

    private Long userId;

    private Integer totalPoints;

    private Integer badgeCount;

    private Integer currentStreak;

    private LocalDate lastCheckInDate;

    private List<RewardBadgeResponse> badges;
}
