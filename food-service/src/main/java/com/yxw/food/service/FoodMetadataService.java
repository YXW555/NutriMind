package com.yxw.food.service;

import com.yxw.food.dto.FoodCategoryResponse;
import com.yxw.food.dto.FoodMetadataResponse;
import com.yxw.food.dto.FoodRecognitionLogResponse;

import java.util.List;

public interface FoodMetadataService {

    List<FoodCategoryResponse> listCategories();

    FoodMetadataResponse getFoodMetadata(Long foodId);

    List<FoodRecognitionLogResponse> listRecognitionLogs(Long foodId, int size);
}
