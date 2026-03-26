package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("advisor_message")
public class AdvisorMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String role;

    private String content;

    private String referencesJson;

    private LocalDateTime createdAt;
}
