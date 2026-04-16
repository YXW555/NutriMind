package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodGraphRelationTypeSummaryResponse {

    private String relationType;

    private Long count;
}
