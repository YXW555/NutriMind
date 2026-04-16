package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FoodGraphProfileResponse {

    private Long foodId;

    private String foodName;

    private String categoryName;

    private String conceptName;

    private Long relationCount;

    private List<FoodGraphRelationResponse> relations;
}
