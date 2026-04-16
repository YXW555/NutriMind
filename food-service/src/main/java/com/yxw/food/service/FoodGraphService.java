package com.yxw.food.service;

import com.yxw.food.dto.FoodGraphOverviewResponse;
import com.yxw.food.dto.FoodGraphProfileResponse;
import com.yxw.food.dto.FoodGraphRelationResponse;
import com.yxw.food.dto.FoodGraphSyncResponse;
import com.yxw.food.dto.KnowledgeSourceResponse;

import java.util.List;

public interface FoodGraphService {

    FoodGraphOverviewResponse getOverview();

    List<FoodGraphRelationResponse> listRelations(String keyword, String relationType, Integer size);

    FoodGraphProfileResponse getFoodGraphProfile(Long foodId, Integer size);

    List<KnowledgeSourceResponse> listKnowledgeSources();

    FoodGraphSyncResponse syncGraph();
}
