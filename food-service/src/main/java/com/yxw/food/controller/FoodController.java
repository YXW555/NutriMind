package com.yxw.food.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.food.dto.FoodUpsertRequest;
import com.yxw.food.service.FoodBasicService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/foods")
public class FoodController {

    private final FoodBasicService foodBasicService;

    public FoodController(FoodBasicService foodBasicService) {
        this.foodBasicService = foodBasicService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FoodNutritionSnapshot>> listFoods(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(foodBasicService.searchFoods(keyword, category, current, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<FoodNutritionSnapshot> getFood(@PathVariable Long id) {
        return ApiResponse.success(foodBasicService.getFood(id));
    }

    @PostMapping
    public ApiResponse<FoodNutritionSnapshot> createFood(@Valid @RequestBody FoodUpsertRequest request) {
        return ApiResponse.success("食物创建成功", foodBasicService.createFood(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FoodNutritionSnapshot> updateFood(@PathVariable Long id,
                                                         @Valid @RequestBody FoodUpsertRequest request) {
        return ApiResponse.success("食物更新成功", foodBasicService.updateFood(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFood(@PathVariable Long id) {
        foodBasicService.deleteFood(id);
        return ApiResponse.success("食物删除成功", null);
    }
}
