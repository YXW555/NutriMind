package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.RewardSummaryResponse;
import com.yxw.meal.service.RewardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meals/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping("/summary")
    public ApiResponse<RewardSummaryResponse> getSummary() {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(rewardService.getSummary(currentUserId));
    }
}
