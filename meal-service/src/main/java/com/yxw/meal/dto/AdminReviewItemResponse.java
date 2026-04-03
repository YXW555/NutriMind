package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminReviewItemResponse {

    private String id;

    private String type;

    private String targetId;

    private String author;

    private String risk;

    private String reason;

    private String status;

    private String contentPreview;
}
