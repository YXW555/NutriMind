package com.yxw.ai.client;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component
public class FoodCatalogClient {

    private static final ParameterizedTypeReference<ApiResponse<PageResponse<FoodNutritionSnapshot>>> FOOD_PAGE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient foodServiceRestClient;

    public FoodCatalogClient(RestClient foodServiceRestClient) {
        this.foodServiceRestClient = foodServiceRestClient;
    }

    public List<FoodNutritionSnapshot> searchFoods(String keyword, int size) {
        ApiResponse<PageResponse<FoodNutritionSnapshot>> response = foodServiceRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/foods")
                            .queryParam("current", 1)
                            .queryParam("size", Math.max(size, 1));
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
}
