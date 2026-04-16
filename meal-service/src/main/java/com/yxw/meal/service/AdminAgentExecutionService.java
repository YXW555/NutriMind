package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.meal.dto.AdminAgentExecutionDetailResponse;
import com.yxw.meal.dto.AdminAgentExecutionStepResponse;
import com.yxw.meal.dto.AdminAgentExecutionSummaryResponse;
import com.yxw.meal.entity.AgentExecution;
import com.yxw.meal.entity.AgentExecutionStep;
import com.yxw.meal.mapper.AgentExecutionMapper;
import com.yxw.meal.mapper.AgentExecutionStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAgentExecutionService {

    private final AgentExecutionMapper agentExecutionMapper;
    private final AgentExecutionStepMapper agentExecutionStepMapper;

    public AdminAgentExecutionService(AgentExecutionMapper agentExecutionMapper,
                                      AgentExecutionStepMapper agentExecutionStepMapper) {
        this.agentExecutionMapper = agentExecutionMapper;
        this.agentExecutionStepMapper = agentExecutionStepMapper;
    }

    public List<AdminAgentExecutionSummaryResponse> listExecutions(String sceneType, int size) {
        int limit = Math.max(1, Math.min(size, 30));
        List<AgentExecution> executions = agentExecutionMapper.selectList(new LambdaQueryWrapper<AgentExecution>()
                .eq(StringUtils.hasText(sceneType), AgentExecution::getSceneType, sceneType == null ? null : sceneType.trim().toUpperCase(Locale.ROOT))
                .orderByDesc(AgentExecution::getCreatedAt)
                .orderByDesc(AgentExecution::getId)
                .last("LIMIT " + limit));

        if (executions.isEmpty()) {
            return List.of();
        }

        List<Long> executionIds = executions.stream().map(AgentExecution::getId).toList();
        Map<Long, Long> stepCountMap = agentExecutionStepMapper.selectList(new LambdaQueryWrapper<AgentExecutionStep>()
                        .in(AgentExecutionStep::getExecutionId, executionIds))
                .stream()
                .collect(Collectors.groupingBy(AgentExecutionStep::getExecutionId, Collectors.counting()));

        return executions.stream()
                .map(execution -> AdminAgentExecutionSummaryResponse.builder()
                        .id(execution.getId())
                        .userId(execution.getUserId())
                        .sceneType(execution.getSceneType())
                        .requestSummary(execution.getRequestSummary())
                        .generationMode(execution.getGenerationMode())
                        .finalStatus(execution.getFinalStatus())
                        .finalSummary(execution.getFinalSummary())
                        .stepCount(stepCountMap.getOrDefault(execution.getId(), 0L).intValue())
                        .createdAt(execution.getCreatedAt())
                        .updatedAt(execution.getUpdatedAt())
                        .build())
                .toList();
    }

    public AdminAgentExecutionDetailResponse getExecutionDetail(Long executionId) {
        AgentExecution execution = agentExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("agent execution not found");
        }

        List<AdminAgentExecutionStepResponse> steps = agentExecutionStepMapper.selectList(new LambdaQueryWrapper<AgentExecutionStep>()
                        .eq(AgentExecutionStep::getExecutionId, executionId)
                        .orderByAsc(AgentExecutionStep::getStepOrder)
                        .orderByAsc(AgentExecutionStep::getId))
                .stream()
                .map(step -> AdminAgentExecutionStepResponse.builder()
                        .stepOrder(step.getStepOrder())
                        .agentName(step.getAgentName())
                        .stageName(step.getStageName())
                        .status(step.getStatus())
                        .inputSummary(step.getInputSummary())
                        .outputSummary(step.getOutputSummary())
                        .referenceSummary(step.getReferenceSummary())
                        .durationMs(step.getDurationMs())
                        .createdAt(step.getCreatedAt())
                        .build())
                .toList();

        return AdminAgentExecutionDetailResponse.builder()
                .id(execution.getId())
                .userId(execution.getUserId())
                .sceneType(execution.getSceneType())
                .requestSummary(execution.getRequestSummary())
                .generationMode(execution.getGenerationMode())
                .finalStatus(execution.getFinalStatus())
                .finalSummary(execution.getFinalSummary())
                .createdAt(execution.getCreatedAt())
                .updatedAt(execution.getUpdatedAt())
                .steps(steps)
                .build();
    }
}
