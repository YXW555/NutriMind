package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.ReportOverviewResponse;
import com.yxw.meal.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/overview")
    public ApiResponse<ReportOverviewResponse> getOverview(@RequestParam(defaultValue = "week") String rangeType) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(reportService.getOverview(currentUserId, rangeType));
    }
}
