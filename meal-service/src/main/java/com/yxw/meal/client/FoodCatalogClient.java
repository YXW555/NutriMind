package com.yxw.meal.client;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FoodCatalogClient {

    private static final ParameterizedTypeReference<ApiResponse<FoodNutritionSnapshot>> FOOD_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient foodServiceRestClient;

    public FoodCatalogClient(@Qualifier("foodServiceRestClient") RestClient foodServiceRestClient) {
        this.foodServiceRestClient = foodServiceRestClient;
    }

    public FoodNutritionSnapshot getFoodById(Long foodId) {
        ApiResponse<FoodNutritionSnapshot> response = foodServiceRestClient.get()
                .uri("/foods/{id}", foodId)
                .retrieve()
                .body(FOOD_RESPONSE_TYPE);
        if (response == null || response.getCode() == null || response.getCode() != 200 || response.getData() == null) {
            throw new IllegalArgumentException("failed to fetch food: " + foodId);
        }
        return response.getData();
    }
}
