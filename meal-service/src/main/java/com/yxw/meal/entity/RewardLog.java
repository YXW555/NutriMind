package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reward_log")
public class RewardLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String eventType;

    private String bizKey;

    private Integer points;

    private String title;

    private String description;

    private LocalDate recordDate;

    private LocalDateTime createdAt;
}
