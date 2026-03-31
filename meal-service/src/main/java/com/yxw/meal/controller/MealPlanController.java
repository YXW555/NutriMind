package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.MealPlanApplyRequest;
import com.yxw.meal.dto.MealPlanDaySummaryResponse;
import com.yxw.meal.dto.MealPlanGenerateDailyRequest;
import com.yxw.meal.dto.MealPlanGenerateWeekRequest;
import com.yxw.meal.dto.MealPlanResponse;
import com.yxw.meal.dto.MealPlanSaveRequest;
import com.yxw.meal.dto.MealRecordResponse;
import com.yxw.meal.dto.GeneratedMealPlanWeekResponse;
import com.yxw.meal.service.MealPlanService;
import com.yxw.meal.service.MealPlanningAgentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/meals/plans")
public class MealPlanController {

    private final MealPlanService mealPlanService;
    private final MealPlanningAgentService mealPlanningAgentService;

    public MealPlanController(MealPlanService mealPlanService,
                              MealPlanningAgentService mealPlanningAgentService) {
        this.mealPlanService = mealPlanService;
        this.mealPlanningAgentService = mealPlanningAgentService;
    }

    @GetMapping("/daily")
    public ApiResponse<MealPlanResponse> getDailyPlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(mealPlanService.getDailyPlan(currentUserId, planDate));
    }

    @GetMapping("/week")
    public ApiResponse<List<MealPlanDaySummaryResponse>> getWeekPlans(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchorDate) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(mealPlanService.listWeekPlans(currentUserId, anchorDate));
    }

    @PutMapping("/daily")
    public ApiResponse<MealPlanResponse> saveDailyPlan(@Valid @RequestBody MealPlanSaveRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("meal plan saved", mealPlanService.saveDailyPlan(currentUserId, request));
    }

    @PostMapping("/daily/apply")
    public ApiResponse<MealRecordResponse> applyDailyPlan(@Valid @RequestBody MealPlanApplyRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("meal plan applied", mealPlanService.applyDailyPlan(currentUserId, request));
    }

    @PostMapping("/generate/daily")
    public ApiResponse<Object> generateDailyPlan(@Valid @RequestBody MealPlanGenerateDailyRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(mealPlanningAgentService.generateDaily(currentUserId, request));
    }

    @PostMapping("/generate/week")
    public ApiResponse<GeneratedMealPlanWeekResponse> generateWeekPlan(@Valid @RequestBody MealPlanGenerateWeekRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(mealPlanningAgentService.generateWeek(currentUserId, request));
    }
}
