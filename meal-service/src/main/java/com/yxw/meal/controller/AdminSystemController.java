package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.meal.dto.AdminSystemOverviewResponse;
import com.yxw.meal.service.AdminSystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system")
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    public AdminSystemController(AdminSystemService adminSystemService) {
        this.adminSystemService = adminSystemService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminSystemOverviewResponse> getOverview() {
        return ApiResponse.success(adminSystemService.getOverview());
    }
}
