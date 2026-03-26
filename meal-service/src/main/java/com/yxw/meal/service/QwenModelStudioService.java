package com.yxw.meal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yxw.meal.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QwenModelStudioService {

    private static final Logger log = LoggerFactory.getLogger(QwenModelStudioService.class);

    private final RagProperties ragProperties;

    public QwenModelStudioService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public boolean isReady() {
        return ragProperties.isEnabled()
                && ragProperties.getQwen().isEnabled()
                && StringUtils.hasText(ragProperties.getQwen().getApiKey());
    }

    public List<Float> embedQuery(String query) {
        return embedTexts(List.of(query)).get(0);
    }

    public List<List<Float>> embedTexts(List<String> texts) {
        if (!isReady()) {
            throw new IllegalStateException("Qwen embedding is not ready. Please configure APP_RAG_QWEN_API_KEY.");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<Float>> embeddings = new ArrayList<>();
        int batchSize = Math.max(1, ragProperties.getQwen().getEmbeddingBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(texts.size(), start + batchSize);
            embeddings.addAll(requestEmbeddings(texts.subList(start, end)));
        }
        return embeddings;
    }

    public String generateAdvisorAnswer(String systemPrompt, String userPrompt) {
        if (!isReady()) {
            throw new IllegalStateException("Qwen chat is not ready. Please configure APP_RAG_QWEN_API_KEY.");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", ragProperties.getQwen().getChatModel());
        requestBody.put("temperature", ragProperties.getQwen().getTemperature());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        JsonNode response = buildClient().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("Qwen returned an empty chat response.");
        }

        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            log.warn("Qwen chat response did not contain message content: {}", response);
            throw new IllegalStateException("Qwen returned an empty answer.");
        }
        return content.trim();
    }

    private List<List<Float>> requestEmbeddings(List<String> texts) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", ragProperties.getQwen().getEmbeddingModel());
        requestBody.put("input", texts.size() == 1 ? texts.get(0) : texts);
        requestBody.put("dimensions", ragProperties.getQwen().getEmbeddingDimension());

        JsonNode response = buildClient().post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("Qwen returned an empty embedding response.");
        }

        JsonNode dataNode = response.path("data");
        if (!dataNode.isArray() || dataNode.isEmpty()) {
            throw new IllegalStateException("Qwen embedding response does not contain vectors.");
        }

        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : dataNode) {
            JsonNode embeddingNode = item.path("embedding");
            List<Float> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                vector.add((float) value.asDouble());
            }
            embeddings.add(vector);
        }
        return embeddings;
    }

    private RestClient buildClient() {
        return RestClient.builder()
                .baseUrl(ragProperties.getQwen().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ragProperties.getQwen().getApiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
