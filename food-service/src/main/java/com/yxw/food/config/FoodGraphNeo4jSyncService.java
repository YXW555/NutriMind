package com.yxw.food.config;

import com.yxw.food.dto.FoodGraphSyncResponse;

public interface FoodGraphNeo4jSyncService {

    FoodGraphSyncResponse syncFromRelationalGraph();
}
