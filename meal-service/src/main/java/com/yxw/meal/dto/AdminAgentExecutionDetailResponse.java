package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminAgentExecutionDetailResponse {

    private Long id;

    private Long userId;

    private String sceneType;

    private String requestSummary;

    private String generationMode;

    private String finalStatus;

    private String finalSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<AdminAgentExecutionStepResponse> steps;
}
