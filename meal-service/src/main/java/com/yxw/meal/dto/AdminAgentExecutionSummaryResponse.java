package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminAgentExecutionSummaryResponse {

    private Long id;

    private Long userId;

    private String sceneType;

    private String requestSummary;

    private String generationMode;

    private String finalStatus;

    private String finalSummary;

    private Integer stepCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
