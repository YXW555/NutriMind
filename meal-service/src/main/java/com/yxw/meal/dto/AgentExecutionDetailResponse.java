package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgentExecutionDetailResponse {

    private Long id;

    private String sceneType;

    private String generationMode;

    private String finalStatus;

    private String finalSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<AgentExecutionStepResponse> steps;
}
