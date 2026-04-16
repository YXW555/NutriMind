package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentExecutionStepResponse {

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
