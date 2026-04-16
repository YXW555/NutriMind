package com.yxw.ai.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxw.ai.client.PythonInferenceClient;
import com.yxw.ai.client.QwenVisionClient;
import com.yxw.ai.config.VisionProperties;
import com.yxw.ai.dto.PythonInferencePrediction;
import com.yxw.ai.dto.PythonInferenceResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.vision", name = "engine", havingValue = "qwen")
public class QwenVisionRecognitionEngine implements FoodRecognitionEngine {

    private static final String DEFAULT_REASON = "基于 qwen3-vl-plus 的多模态食物识别结果";
    private static final String HYBRID_REASON = "已结合本地深度学习模型候选与 qwen3-vl-plus 进行融合判断";

    private final QwenVisionClient qwenVisionClient;
    private final PythonInferenceClient pythonInferenceClient;
    private final VisionProperties visionProperties;
    private final ObjectMapper objectMapper;

    public QwenVisionRecognitionEngine(QwenVisionClient qwenVisionClient,
                                       PythonInferenceClient pythonInferenceClient,
                                       VisionProperties visionProperties,
                                       ObjectMapper objectMapper) {
        this.qwenVisionClient = qwenVisionClient;
        this.pythonInferenceClient = pythonInferenceClient;
        this.visionProperties = visionProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecognitionEngineResult recognize(MultipartFile file, int topK) {
        if (!visionProperties.getQwen().isEnabled()) {
            throw new IllegalStateException("Qwen vision engine is disabled.");
        }

        List<LocalModelHint> localHints = loadLocalModelHints(file);
        JsonNode response = qwenVisionClient.chatCompletions(buildRequestBody(file, topK, localHints));
        String rawContent = extractContent(response);
        if (!StringUtils.hasText(rawContent)) {
            throw new IllegalStateException("Qwen returned an empty vision result.");
        }

        QwenRecognitionPayload payload = parsePayload(rawContent);
        List<RecognitionEngineCandidate> candidates = toCandidates(payload, topK, localHints);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Qwen vision returned no usable candidates.");
        }

        return RecognitionEngineResult.builder()
                .recognitionMode(localHints.isEmpty() ? "QWEN3_VL_PLUS" : "QWEN3_VL_PLUS_LOCAL_MODEL")
                .candidates(candidates)
                .cookingMethod(cleanText(payload.cookingMethod))
                .estimatedWeightGrams(normalizeInteger(payload.estimatedWeightGrams))
                .estimatedWeightMinGrams(normalizeInteger(payload.estimatedWeightMinGrams))
                .estimatedWeightMaxGrams(normalizeInteger(payload.estimatedWeightMaxGrams))
                .portionDescription(cleanText(payload.portionDescription))
                .build();
    }

    private List<LocalModelHint> loadLocalModelHints(MultipartFile file) {
        if (!visionProperties.getQwen().isPreclassifierEnabled()) {
            return List.of();
        }

        int topK = Math.max(1, Math.min(visionProperties.getQwen().getPreclassifierTopK(), 5));
        try {
            PythonInferenceResponse response = pythonInferenceClient.predict(file, topK);
            if (response == null || response.getPredictions() == null || response.getPredictions().isEmpty()) {
                return List.of();
            }

            List<LocalModelHint> hints = new ArrayList<>();
            for (PythonInferencePrediction prediction : response.getPredictions()) {
                if (prediction == null) {
                    continue;
                }
                String canonicalLabel = cleanText(prediction.getCanonicalLabel());
                String label = cleanText(prediction.getLabel());
                if (!StringUtils.hasText(canonicalLabel) && !StringUtils.hasText(label)) {
                    continue;
                }

                hints.add(new LocalModelHint(
                        label,
                        canonicalLabel,
                        prediction.getConfidence(),
                        normalizeTerms(prediction.getAliases()),
                        normalizeTerms(prediction.getSearchKeywords()),
                        cleanText(prediction.getMatchReason()),
                        cleanText(prediction.getSource()),
                        cleanText(response.getMode())
                ));
            }
            return hints;
        } catch (Exception ignored) {
            // Local model is a prior hint for Qwen, not a hard dependency.
            return List.of();
        }
    }

    private Map<String, Object> buildRequestBody(MultipartFile file, int topK, List<LocalModelHint> localHints) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", visionProperties.getQwen().getModel());
        requestBody.put("temperature", visionProperties.getQwen().getTemperature());
        requestBody.put("max_tokens", visionProperties.getQwen().getMaxTokens());
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", buildSystemPrompt(topK, !localHints.isEmpty())
                ),
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", buildUserPrompt(topK, localHints)),
                                Map.of("type", "image_url", "image_url", Map.of("url", buildDataUrl(file)))
                        )
                )
        ));
        return requestBody;
    }

    private String buildSystemPrompt(int topK, boolean hasLocalHints) {
        return """
                你是一名专业的中文食物识别助手。请根据图片识别食物，并严格输出 JSON。

                识别目标：
                1. 优先输出用户真正能看懂、能确认的中文食物名称。
                2. 如果图片是具体菜品，请尽量给出菜名，而不是抽象英文特征。
                3. 尽量识别烹饪方式，如炒、炸、蒸、煮、烤、凉拌等。
                4. 估算主要食物的重量，给出总估值和区间；无法判断时返回 null。
                5. 给出不超过 %d 个候选结果，按置信度从高到低排序。
                %s

                输出要求：
                - 只能输出 JSON，不要输出 markdown，不要输出解释文本。
                - confidence 使用 0 到 1 的小数。
                - canonical_label 必须尽量使用标准中文名称。

                JSON 结构如下：
                {
                  "primary_label": "主识别结果的中文名称",
                  "primary_canonical_label": "标准中文名称",
                  "primary_aliases": ["别名1", "别名2"],
                  "primary_search_keywords": ["搜索词1", "搜索词2"],
                  "confidence": 0.0,
                  "reason": "一句中文原因",
                  "cooking_method": "炒/炸/蒸/煮/烤/凉拌等，无法判断时为 null",
                  "portion_description": "例如 约1份 / 约1人份，无法判断时为 null",
                  "estimated_weight_grams": 160,
                  "estimated_weight_min_grams": 140,
                  "estimated_weight_max_grams": 180,
                  "candidates": [
                    {
                      "label": "候选中文名称",
                      "canonical_label": "标准中文名称",
                      "aliases": ["别名"],
                      "search_keywords": ["搜索词"],
                      "confidence": 0.0,
                      "reason": "一句中文原因"
                    }
                  ]
                }
                """.formatted(
                topK,
                hasLocalHints
                        ? "注意：用户系统已提供一组本地深度学习模型候选，它们是视觉先验，不一定完全正确。请结合图片内容进行校正、筛选和细化，不要机械照抄。"
                        : "如果图片存在歧义，请给出最常见、最适合作为饮食记录的中文菜名。"
        );
    }

    private String buildUserPrompt(int topK, List<LocalModelHint> localHints) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请识别这张食物图片，返回最多 ")
                .append(topK)
                .append(" 个中文候选食物，并估算做法、份量和重量区间。");

        if (!localHints.isEmpty()) {
            prompt.append("\n\n以下是本地深度学习模型给出的初筛候选，可作为参考，但你需要结合图片重新判断：");
            int index = 1;
            for (LocalModelHint hint : localHints) {
                prompt.append("\n")
                        .append(index++)
                        .append(". 标签=")
                        .append(firstNonBlank(hint.canonicalLabel(), hint.label(), "未知"))
                        .append("；置信度=")
                        .append(formatConfidence(hint.confidence()));
                if (!hint.aliases().isEmpty()) {
                    prompt.append("；别名=").append(String.join("、", hint.aliases()));
                }
                if (!hint.searchKeywords().isEmpty()) {
                    prompt.append("；检索词=").append(String.join("、", hint.searchKeywords()));
                }
                if (StringUtils.hasText(hint.reason())) {
                    prompt.append("；原因=").append(hint.reason());
                }
                if (StringUtils.hasText(hint.mode())) {
                    prompt.append("；模式=").append(hint.mode());
                }
            }

            prompt.append("\n\n请特别处理以下情况：")
                    .append("\n- 如果本地模型给出的是英文视觉类别或抽象特征，请把它转换成更自然的中文食物名称。")
                    .append("\n- 如果本地模型候选不够准确，请以图片实际内容为准。")
                    .append("\n- 最终返回结果必须适合直接给普通用户确认。");
        }

        return prompt.toString();
    }

    private String buildDataUrl(MultipartFile file) {
        try {
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType().trim()
                    : MediaType.IMAGE_JPEG_VALUE;
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + contentType + ";base64," + encoded;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded image", ex);
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    builder.append(item.asText()).append('\n');
                    continue;
                }
                JsonNode textNode = item.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText()).append('\n');
                }
            }
            return builder.toString().trim();
        }
        return contentNode.toString();
    }

    private QwenRecognitionPayload parsePayload(String rawContent) {
        String sanitized = sanitizeJson(rawContent);
        try {
            return objectMapper.readValue(sanitized, QwenRecognitionPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse Qwen vision response: " + rawContent, ex);
        }
    }

    private String sanitizeJson(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```json\\s*", "");
            content = content.replaceFirst("^```\\s*", "");
            content = content.replaceFirst("\\s*```$", "");
        }
        return content.trim();
    }

    private List<RecognitionEngineCandidate> toCandidates(QwenRecognitionPayload payload,
                                                          int topK,
                                                          List<LocalModelHint> localHints) {
        List<RecognitionEngineCandidate> candidates = new ArrayList<>();
        LinkedHashSet<String> dedup = new LinkedHashSet<>();

        if (StringUtils.hasText(payload.primaryLabel) || StringUtils.hasText(payload.primaryCanonicalLabel)) {
            RecognitionEngineCandidate primary = RecognitionEngineCandidate.builder()
                    .label(firstNonBlank(payload.primaryLabel, payload.primaryCanonicalLabel))
                    .canonicalLabel(firstNonBlank(payload.primaryCanonicalLabel, payload.primaryLabel))
                    .aliases(mergeTerms(payload.primaryAliases, localHints.isEmpty() ? List.of() : localHints.get(0).aliases()))
                    .searchKeywords(buildSearchKeywords(
                            payload.primarySearchKeywords,
                            payload.primaryAliases,
                            payload.primaryCanonicalLabel,
                            payload.primaryLabel,
                            payload.cookingMethod,
                            localHints
                    ))
                    .confidence(normalizeConfidence(payload.confidence, BigDecimal.valueOf(0.82)))
                    .matchReason(buildReason(payload.reason, !localHints.isEmpty()))
                    .source(localHints.isEmpty() ? "qwen3-vl-plus" : "qwen3-vl-plus+local-model")
                    .build();
            if (registerCandidateKey(dedup, primary)) {
                candidates.add(primary);
            }
        }

        if (payload.candidates != null) {
            for (QwenCandidatePayload item : payload.candidates) {
                RecognitionEngineCandidate candidate = RecognitionEngineCandidate.builder()
                        .label(firstNonBlank(item.label, item.canonicalLabel))
                        .canonicalLabel(firstNonBlank(item.canonicalLabel, item.label))
                        .aliases(normalizeTerms(item.aliases))
                        .searchKeywords(buildSearchKeywords(
                                item.searchKeywords,
                                item.aliases,
                                item.canonicalLabel,
                                item.label,
                                payload.cookingMethod,
                                localHints
                        ))
                        .confidence(normalizeConfidence(item.confidence, BigDecimal.valueOf(0.65)))
                        .matchReason(buildReason(item.reason, !localHints.isEmpty()))
                        .source(localHints.isEmpty() ? "qwen3-vl-plus" : "qwen3-vl-plus+local-model")
                        .build();
                if (!StringUtils.hasText(candidate.getLabel()) && !StringUtils.hasText(candidate.getCanonicalLabel())) {
                    continue;
                }
                if (registerCandidateKey(dedup, candidate)) {
                    candidates.add(candidate);
                }
                if (candidates.size() >= topK) {
                    break;
                }
            }
        }

        return candidates.stream().limit(topK).toList();
    }

    private String buildReason(String reason, boolean hybrid) {
        String base = firstNonBlank(reason, DEFAULT_REASON);
        return hybrid ? base + "；" + HYBRID_REASON : base;
    }

    private boolean registerCandidateKey(LinkedHashSet<String> dedup, RecognitionEngineCandidate candidate) {
        String key = normalizeKey(firstNonBlank(candidate.getCanonicalLabel(), candidate.getLabel()));
        if (!StringUtils.hasText(key) || dedup.contains(key)) {
            return false;
        }
        dedup.add(key);
        return true;
    }

    private List<String> buildSearchKeywords(List<String> keywords,
                                             List<String> aliases,
                                             String canonicalLabel,
                                             String label,
                                             String cookingMethod,
                                             List<LocalModelHint> localHints) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addTerms(values, keywords);
        addTerms(values, aliases);
        addTerms(values, List.of(canonicalLabel, label));
        if (StringUtils.hasText(cookingMethod) && StringUtils.hasText(canonicalLabel)) {
            values.add(cookingMethod.trim() + canonicalLabel.trim());
        }
        if (StringUtils.hasText(cookingMethod)) {
            values.add(cookingMethod.trim());
        }
        for (LocalModelHint hint : localHints) {
            addTerms(values, hint.searchKeywords());
            addTerms(values, hint.aliases());
            addTerms(values, List.of(hint.canonicalLabel(), hint.label()));
        }
        return values.stream().limit(10).toList();
    }

    private void addTerms(LinkedHashSet<String> target, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                target.add(value.trim());
            }
        }
    }

    private List<String> normalizeTerms(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized.stream().toList();
    }

    private List<String> mergeTerms(List<String> primary, List<String> secondary) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addTerms(merged, primary);
        addTerms(merged, secondary);
        return merged.stream().limit(8).toList();
    }

    private BigDecimal normalizeConfidence(Double value, BigDecimal fallback) {
        BigDecimal confidence = value == null ? fallback : BigDecimal.valueOf(value);
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            confidence = BigDecimal.ZERO;
        }
        if (confidence.compareTo(BigDecimal.ONE) > 0) {
            confidence = BigDecimal.ONE;
        }
        return confidence.setScale(2, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer normalizeInteger(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
    }

    private String formatConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return "-";
        }
        return confidence.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static class QwenRecognitionPayload {
        @JsonProperty("primary_label")
        public String primaryLabel;
        @JsonProperty("primary_canonical_label")
        public String primaryCanonicalLabel;
        @JsonProperty("primary_aliases")
        public List<String> primaryAliases;
        @JsonProperty("primary_search_keywords")
        public List<String> primarySearchKeywords;
        public Double confidence;
        public String reason;
        @JsonProperty("cooking_method")
        public String cookingMethod;
        @JsonProperty("portion_description")
        public String portionDescription;
        @JsonProperty("estimated_weight_grams")
        public Integer estimatedWeightGrams;
        @JsonProperty("estimated_weight_min_grams")
        public Integer estimatedWeightMinGrams;
        @JsonProperty("estimated_weight_max_grams")
        public Integer estimatedWeightMaxGrams;
        public List<QwenCandidatePayload> candidates;
    }

    private static class QwenCandidatePayload {
        public String label;
        @JsonProperty("canonical_label")
        public String canonicalLabel;
        public List<String> aliases;
        @JsonProperty("search_keywords")
        public List<String> searchKeywords;
        public Double confidence;
        public String reason;
    }

    private record LocalModelHint(String label,
                                  String canonicalLabel,
                                  BigDecimal confidence,
                                  List<String> aliases,
                                  List<String> searchKeywords,
                                  String reason,
                                  String source,
                                  String mode) {
    }
}
