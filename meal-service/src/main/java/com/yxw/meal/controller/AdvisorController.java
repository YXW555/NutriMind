package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.AdvisorMessageRequest;
import com.yxw.meal.dto.AdvisorMessageResponse;
import com.yxw.meal.service.AdvisorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/advisor")
public class AdvisorController {

    private final AdvisorService advisorService;

    public AdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<AdvisorMessageResponse>> listMessages() {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(advisorService.listMessages(currentUserId));
    }

    @PostMapping("/messages")
    public ApiResponse<AdvisorMessageResponse> sendMessage(@Valid @RequestBody AdvisorMessageRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("顾问已回复", advisorService.sendMessage(currentUserId, request));
    }
}
