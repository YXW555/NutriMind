package com.yxw.meal.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class FoodGraphProfileSnapshot {

    private Long foodId;

    private String foodName;

    private String categoryName;

    private String conceptName;

    private Long relationCount;

    private List<FoodGraphRelationSnapshot> relations;
}
