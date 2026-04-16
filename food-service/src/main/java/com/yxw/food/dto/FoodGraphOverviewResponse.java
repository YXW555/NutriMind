package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FoodGraphOverviewResponse {

    private String backend;

    private Boolean neo4jReady;

    private Long foodNodeCount;

    private Long graphNodeCount;

    private Long relationCount;

    private Long knowledgeSourceCount;

    private Long syncLogCount;

    private List<FoodGraphRelationTypeSummaryResponse> relationTypeSummary;

    private List<FoodGraphRelationResponse> sampleRelations;
}
