package com.yxw.ai.client.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FoodCatalogCreateRequest {

    private String name;

    private String category;

    private Long ownerUserId;

    private String sourceType;

    private String unit;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private BigDecimal fiber;

    private Integer status;
}
