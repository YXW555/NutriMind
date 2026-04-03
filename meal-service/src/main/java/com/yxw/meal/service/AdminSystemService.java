package com.yxw.meal.service;

import com.yxw.meal.config.RagProperties;
import com.yxw.meal.dto.AdminKnowledgeDocumentResponse;
import com.yxw.meal.dto.AdminSystemOverviewResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminSystemService {

    private final RagProperties ragProperties;
    private final Environment environment;
    private final AdminKnowledgeService adminKnowledgeService;
    private final AdminReviewService adminReviewService;
    private final MilvusKnowledgeStoreService milvusKnowledgeStoreService;

    public AdminSystemService(RagProperties ragProperties,
                              Environment environment,
                              AdminKnowledgeService adminKnowledgeService,
                              AdminReviewService adminReviewService,
                              MilvusKnowledgeStoreService milvusKnowledgeStoreService) {
        this.ragProperties = ragProperties;
        this.environment = environment;
        this.adminKnowledgeService = adminKnowledgeService;
        this.adminReviewService = adminReviewService;
        this.milvusKnowledgeStoreService = milvusKnowledgeStoreService;
    }

    public AdminSystemOverviewResponse getOverview() {
        List<AdminKnowledgeDocumentResponse> documents = adminKnowledgeService.listDocuments();
        int enabledCount = (int) documents.stream()
                .filter(document -> "已启用".equals(document.getStatus()))
                .count();
        int pendingReviewCount = (int) adminReviewService.listItems().stream()
                .filter(item -> "待审核".equals(item.getStatus()))
                .count();

        return AdminSystemOverviewResponse.builder()
                .ragEnabled(ragProperties.isEnabled())
                .qwenEnabled(ragProperties.getQwen().isEnabled())
                .milvusEnabled(ragProperties.getMilvus().isEnabled())
                .milvusReady(milvusKnowledgeStoreService.isReady())
                .nacosDiscoveryEnabled(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class, false))
                .knowledgeDocumentCount(documents.size())
                .enabledKnowledgeCount(enabledCount)
                .pendingReviewCount(pendingReviewCount)
                .milvusUri(ragProperties.getMilvus().getUri())
                .embeddingModel(ragProperties.getQwen().getEmbeddingModel())
                .chatModel(ragProperties.getQwen().getChatModel())
                .build();
    }
}
