package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_execution")
public class AgentExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sceneType;

    private String requestSummary;

    private String generationMode;

    private String finalStatus;

    private String finalSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
