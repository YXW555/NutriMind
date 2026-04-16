package com.yxw.food.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSourceResponse {

    private Long id;

    private String title;

    private String organization;

    private String sourceType;

    private Integer publishYear;

    private String sourceUrl;

    private String credibilityLevel;

    private String summary;

    private Integer status;
}
