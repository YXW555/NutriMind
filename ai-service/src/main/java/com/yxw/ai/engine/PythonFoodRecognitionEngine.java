package com.yxw.ai.engine;

import com.yxw.ai.client.PythonInferenceClient;
import com.yxw.ai.dto.PythonInferencePrediction;
import com.yxw.ai.dto.PythonInferenceResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.vision", name = "engine", havingValue = "python")
public class PythonFoodRecognitionEngine implements FoodRecognitionEngine {

    private final PythonInferenceClient pythonInferenceClient;

    public PythonFoodRecognitionEngine(PythonInferenceClient pythonInferenceClient) {
        this.pythonInferenceClient = pythonInferenceClient;
    }

    @Override
    public RecognitionEngineResult recognize(MultipartFile file, int topK) {
        PythonInferenceResponse response = pythonInferenceClient.predict(file, topK);
        List<RecognitionEngineCandidate> candidates = response == null || response.getPredictions() == null
                ? Collections.emptyList()
                : response.getPredictions().stream()
                .filter(item -> StringUtils.hasText(item.getLabel()))
                .map(this::toCandidate)
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalStateException("python inference returned no predictions");
        }

        return RecognitionEngineResult.builder()
                .recognitionMode(StringUtils.hasText(response.getMode()) ? response.getMode() : "PYTHON_INFERENCE")
                .candidates(candidates)
                .build();
    }

    private RecognitionEngineCandidate toCandidate(PythonInferencePrediction item) {
        String resolvedLabel = StringUtils.hasText(item.getLabel())
                ? item.getLabel().trim()
                : "";
        String canonicalLabel = StringUtils.hasText(item.getCanonicalLabel())
                ? item.getCanonicalLabel().trim()
                : resolvedLabel;

        return RecognitionEngineCandidate.builder()
                .label(resolvedLabel)
                .canonicalLabel(canonicalLabel)
                .aliases(normalizeTerms(item.getAliases()))
                .searchKeywords(normalizeTerms(item.getSearchKeywords()))
                .confidence(item.getConfidence() == null ? BigDecimal.ZERO : item.getConfidence())
                .matchReason(StringUtils.hasText(item.getMatchReason())
                        ? item.getMatchReason().trim()
                        : "python inference")
                .source(StringUtils.hasText(item.getSource())
                        ? item.getSource().trim()
                        : "python-inference")
                .build();
    }

    private List<String> normalizeTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String term : terms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }

            String value = term.trim();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }
}
