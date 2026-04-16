package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.meal.dto.AdminAgentExecutionDetailResponse;
import com.yxw.meal.dto.AdminAgentExecutionSummaryResponse;
import com.yxw.meal.service.AdminAgentExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/agent-executions")
public class AdminAgentExecutionController {

    private final AdminAgentExecutionService adminAgentExecutionService;

    public AdminAgentExecutionController(AdminAgentExecutionService adminAgentExecutionService) {
        this.adminAgentExecutionService = adminAgentExecutionService;
    }

    @GetMapping
    public ApiResponse<List<AdminAgentExecutionSummaryResponse>> listExecutions(
            @RequestParam(required = false) String sceneType,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(adminAgentExecutionService.listExecutions(sceneType, size));
    }

    @GetMapping("/{executionId}")
    public ApiResponse<AdminAgentExecutionDetailResponse> getExecutionDetail(@PathVariable Long executionId) {
        return ApiResponse.success(adminAgentExecutionService.getExecutionDetail(executionId));
    }
}
