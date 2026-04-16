package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.meal.dto.AgentExecutionDetailResponse;
import com.yxw.meal.dto.AgentExecutionStepResponse;
import com.yxw.meal.entity.AgentExecution;
import com.yxw.meal.entity.AgentExecutionStep;
import com.yxw.meal.mapper.AgentExecutionMapper;
import com.yxw.meal.mapper.AgentExecutionStepMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentExecutionLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionLogService.class);

    private final AgentExecutionMapper agentExecutionMapper;
    private final AgentExecutionStepMapper agentExecutionStepMapper;

    public AgentExecutionLogService(AgentExecutionMapper agentExecutionMapper,
                                    AgentExecutionStepMapper agentExecutionStepMapper) {
        this.agentExecutionMapper = agentExecutionMapper;
        this.agentExecutionStepMapper = agentExecutionStepMapper;
    }

    public AgentExecutionTracker start(String sceneType, Long userId, String requestSummary) {
        AgentExecution execution = new AgentExecution();
        execution.setUserId(userId);
        execution.setSceneType(StringUtils.hasText(sceneType) ? sceneType.trim() : "UNKNOWN");
        execution.setRequestSummary(limit(requestSummary, 500));
        execution.setFinalStatus("RUNNING");
        safeInsertExecution(execution);
        return new AgentExecutionTracker(execution.getId());
    }

    private void safeInsertExecution(AgentExecution execution) {
        try {
            agentExecutionMapper.insert(execution);
        } catch (RuntimeException exception) {
            log.warn("Failed to create agent execution log", exception);
        }
    }

    public AgentExecutionDetailResponse getExecutionDetail(Long executionId) {
        if (executionId == null) {
            return null;
        }
        AgentExecution execution = agentExecutionMapper.selectById(executionId);
        if (execution == null) {
            return null;
        }

        List<AgentExecutionStepResponse> steps = agentExecutionStepMapper.selectList(new LambdaQueryWrapper<AgentExecutionStep>()
                        .eq(AgentExecutionStep::getExecutionId, executionId)
                        .orderByAsc(AgentExecutionStep::getStepOrder)
                        .orderByAsc(AgentExecutionStep::getId))
                .stream()
                .map(step -> AgentExecutionStepResponse.builder()
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

        return AgentExecutionDetailResponse.builder()
                .id(execution.getId())
                .sceneType(execution.getSceneType())
                .generationMode(execution.getGenerationMode())
                .finalStatus(execution.getFinalStatus())
                .finalSummary(execution.getFinalSummary())
                .createdAt(execution.getCreatedAt())
                .updatedAt(execution.getUpdatedAt())
                .steps(steps)
                .build();
    }

    public final class AgentExecutionTracker {

        private final Long executionId;
        private final AtomicInteger stepCounter = new AtomicInteger(0);

        private AgentExecutionTracker(Long executionId) {
            this.executionId = executionId;
        }

        public Long getExecutionId() {
            return executionId;
        }

        public void step(String agentName,
                         String stageName,
                         String status,
                         String inputSummary,
                         String outputSummary,
                         String referenceSummary,
                         Long durationMs) {
            if (executionId == null) {
                return;
            }
            AgentExecutionStep step = new AgentExecutionStep();
            step.setExecutionId(executionId);
            step.setStepOrder(stepCounter.incrementAndGet());
            step.setAgentName(limit(agentName, 64));
            step.setStageName(limit(stageName, 64));
            step.setStatus(limit(status, 32));
            step.setInputSummary(limit(inputSummary, 3000));
            step.setOutputSummary(limit(outputSummary, 3000));
            step.setReferenceSummary(limit(referenceSummary, 3000));
            step.setDurationMs(durationMs);
            try {
                agentExecutionStepMapper.insert(step);
            } catch (RuntimeException exception) {
                log.warn("Failed to write agent execution step", exception);
            }
        }

        public void complete(String finalStatus, String finalSummary, String generationMode) {
            updateExecution(finalStatus, finalSummary, generationMode);
        }

        public void fail(Throwable throwable) {
            String message = throwable == null ? "Execution failed." : throwable.getMessage();
            updateExecution("FAILED", message, null);
        }

        private void updateExecution(String finalStatus, String finalSummary, String generationMode) {
            if (executionId == null) {
                return;
            }
            AgentExecution execution = new AgentExecution();
            execution.setId(executionId);
            execution.setFinalStatus(limit(finalStatus, 32));
            execution.setFinalSummary(limit(finalSummary, 1000));
            if (StringUtils.hasText(generationMode)) {
                execution.setGenerationMode(limit(generationMode, 64));
            }
            try {
                agentExecutionMapper.updateById(execution);
            } catch (RuntimeException exception) {
                log.warn("Failed to finalize agent execution log", exception);
            }
        }
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }
}
