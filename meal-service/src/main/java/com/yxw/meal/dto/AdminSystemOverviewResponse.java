package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSystemOverviewResponse {

    private boolean ragEnabled;

    private boolean qwenEnabled;

    private boolean milvusEnabled;

    private boolean milvusReady;

    private boolean nacosDiscoveryEnabled;

    private int knowledgeDocumentCount;

    private int enabledKnowledgeCount;

    private int pendingReviewCount;

    private String milvusUri;

    private String embeddingModel;

    private String chatModel;
}
