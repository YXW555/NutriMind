package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodGraphRelationResponse {

    private Long id;

    private String sourceType;

    private String sourceKey;

    private Long sourceRefId;

    private String sourceName;

    private String relationType;

    private String targetType;

    private String targetKey;

    private Long targetRefId;

    private String targetName;

    private String relationValue;

    private String evidenceSummary;

    private Long knowledgeSourceId;

    private String knowledgeSourceTitle;

    private Integer status;
}
