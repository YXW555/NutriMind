package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.meal.dto.AdminReviewItemResponse;
import com.yxw.meal.service.AdminReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/review")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    @GetMapping("/items")
    public ApiResponse<List<AdminReviewItemResponse>> listItems() {
        return ApiResponse.success(adminReviewService.listItems());
    }

    @PutMapping("/{type}/{targetId}")
    public ApiResponse<AdminReviewItemResponse> updateStatus(@PathVariable String type,
                                                             @PathVariable Long targetId,
                                                             @RequestParam String status) {
        return ApiResponse.success(adminReviewService.updateStatus(type, targetId, status));
    }
}
