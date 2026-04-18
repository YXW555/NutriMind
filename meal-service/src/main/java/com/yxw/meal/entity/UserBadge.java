package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_badge")
public class UserBadge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String badgeCode;

    private String badgeName;

    private String badgeDescription;

    private LocalDateTime earnedAt;
}
