package com.yxw.meal.dto;

import com.yxw.meal.entity.UserBadge;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RewardBadgeResponse {

    private String badgeCode;

    private String badgeName;

    private String badgeDescription;

    private LocalDateTime earnedAt;

    public static RewardBadgeResponse from(UserBadge badge) {
        if (badge == null) {
            return null;
        }
        return RewardBadgeResponse.builder()
                .badgeCode(badge.getBadgeCode())
                .badgeName(badge.getBadgeName())
                .badgeDescription(badge.getBadgeDescription())
                .earnedAt(badge.getEarnedAt())
                .build();
    }
}
