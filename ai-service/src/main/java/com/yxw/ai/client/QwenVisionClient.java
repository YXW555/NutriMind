package com.yxw.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.yxw.ai.config.VisionProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class QwenVisionClient {

    private final VisionProperties visionProperties;

    public QwenVisionClient(VisionProperties visionProperties) {
        this.visionProperties = visionProperties;
    }

    public JsonNode chatCompletions(Map<String, Object> requestBody) {
        if (!visionProperties.getQwen().isEnabled() || !StringUtils.hasText(visionProperties.getQwen().getApiKey())) {
            throw new IllegalStateException("Qwen vision is not ready. Please configure APP_VISION_QWEN_API_KEY.");
        }

        return buildClient().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
    }

    private RestClient buildClient() {
        return RestClient.builder()
                .baseUrl(visionProperties.getQwen().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + visionProperties.getQwen().getApiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
