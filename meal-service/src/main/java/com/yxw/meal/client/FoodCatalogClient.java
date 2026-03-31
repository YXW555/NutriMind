package com.yxw.meal.client;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class FoodCatalogClient {

    private static final ParameterizedTypeReference<ApiResponse<FoodNutritionSnapshot>> FOOD_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<ApiResponse<PageResponse<FoodNutritionSnapshot>>> FOOD_PAGE_RESPONSE_TYPE =
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

    public List<FoodNutritionSnapshot> searchFoods(String keyword, int size) {
        ApiResponse<PageResponse<FoodNutritionSnapshot>> response = foodServiceRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/foods")
                            .queryParam("current", 1)
                            .queryParam("size", Math.max(1, size));
                    if (StringUtils.hasText(keyword)) {
                        builder.queryParam("keyword", keyword.trim());
                    }
                    return builder.build();
                })
                .retrieve()
                .body(FOOD_PAGE_RESPONSE_TYPE);

        if (response == null || response.getCode() == null || response.getCode() != 200 || response.getData() == null) {
            return Collections.emptyList();
        }

        List<FoodNutritionSnapshot> records = response.getData().getRecords();
        return records == null ? Collections.emptyList() : records;
    }

    public FoodNutritionSnapshot pickBestMatch(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        List<FoodNutritionSnapshot> candidates = searchFoods(keyword, 10);
        if (candidates.isEmpty()) {
            return null;
        }

        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (FoodNutritionSnapshot candidate : candidates) {
            String name = candidate == null ? null : candidate.getName();
            if (name != null && name.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return candidate;
            }
        }
        for (FoodNutritionSnapshot candidate : candidates) {
            if (candidate != null && Objects.equals(candidate.getName(), keyword.trim())) {
                return candidate;
            }
        }
        return candidates.get(0);
    }
}
