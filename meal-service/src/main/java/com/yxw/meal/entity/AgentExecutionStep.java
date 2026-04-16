package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_execution_step")
public class AgentExecutionStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private Integer stepOrder;

    private String agentName;

    private String stageName;

    private String status;

    private String inputSummary;

    private String outputSummary;

    private String referenceSummary;

    private Long durationMs;

    private LocalDateTime createdAt;
}
