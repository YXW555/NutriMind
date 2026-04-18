package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reward_account")
public class RewardAccount {

    @TableId
    private Long userId;

    private Integer totalPoints;

    private Integer badgeCount;

    private Integer currentStreak;

    private LocalDate lastCheckInDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
