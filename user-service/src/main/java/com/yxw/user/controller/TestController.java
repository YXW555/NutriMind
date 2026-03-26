package com.yxw.user.controller;

import com.yxw.common.core.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success("Hello NutriMind backend");
    }
}
