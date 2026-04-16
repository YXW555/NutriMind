package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodGraphSyncResponse {

    private String backend;

    private String status;

    private String detail;

    private Long nodeCount;

    private Long relationCount;
}
