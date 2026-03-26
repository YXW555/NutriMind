package com.yxw.food.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.food.dto.FoodUpsertRequest;
import com.yxw.food.entity.FoodBasic;

public interface FoodBasicService extends IService<FoodBasic> {

    PageResponse<FoodNutritionSnapshot> searchFoods(String keyword, String category, long current, long size);

    FoodNutritionSnapshot getFood(Long id);

    FoodNutritionSnapshot createFood(FoodUpsertRequest request);

    FoodNutritionSnapshot updateFood(Long id, FoodUpsertRequest request);

    void deleteFood(Long id);
}
