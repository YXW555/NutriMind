package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.CreateMealRequest;
import com.yxw.meal.dto.MealRecordResponse;
import com.yxw.meal.service.MealRecordService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/meals")
public class MealController {

    private final MealRecordService mealRecordService;

    public MealController(MealRecordService mealRecordService) {
        this.mealRecordService = mealRecordService;
    }

    @PostMapping
    public ApiResponse<MealRecordResponse> createMeal(@Valid @RequestBody CreateMealRequest request) {
        Long currentUserId = resolveCurrentUserId(request.getUserId());
        return ApiResponse.success("meal saved", mealRecordService.createMeal(currentUserId, request));
    }

    @GetMapping("/daily")
    public ApiResponse<MealRecordResponse> getDailyMeal(
            @RequestParam(required = false) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordDate) {
        Long currentUserId = resolveCurrentUserId(userId);
        return ApiResponse.success(mealRecordService.getDailyMeal(currentUserId, recordDate));
    }

    @DeleteMapping("/details/{detailId}")
    public ApiResponse<MealRecordResponse> deleteMealDetail(@PathVariable Long detailId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("meal detail deleted", mealRecordService.deleteMealDetail(currentUserId, detailId));
    }

    private Long resolveCurrentUserId(Long requestedUserId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        if (requestedUserId != null && !requestedUserId.equals(currentUserId)) {
            throw new IllegalArgumentException("cross-user access is not allowed");
        }
        return currentUserId;
    }
}
