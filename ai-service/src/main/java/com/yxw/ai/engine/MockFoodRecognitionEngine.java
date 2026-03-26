package com.yxw.ai.engine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.vision", name = "engine", havingValue = "mock", matchIfMissing = true)
public class MockFoodRecognitionEngine implements FoodRecognitionEngine {

    private static final List<String> LABEL_POOL = List.of(
            "米饭", "鸡蛋", "牛奶", "面包", "香蕉", "苹果", "沙拉", "鸡肉", "牛肉", "鱼", "虾", "西兰花", "酸奶", "面条"
    );

    private static final Map<String, String> FILENAME_HINTS = createFilenameHints();

    @Override
    public RecognitionEngineResult recognize(MultipartFile file, int topK) {
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "uploaded-image";

        List<RecognitionEngineCandidate> candidates = new ArrayList<>();
        candidates.addAll(recognizeByFilename(fileName, topK));

        if (candidates.size() < topK) {
            candidates.addAll(recognizeByDigest(file, topK, candidates.size()));
        }

        if (candidates.size() > topK) {
            candidates = new ArrayList<>(candidates.subList(0, topK));
        }

        String mode = candidates.stream().anyMatch(item ->
                StringUtils.hasText(item.getMatchReason()) && item.getMatchReason().contains("filename"))
                ? "MOCK_FILENAME_HINT"
                : "MOCK_IMAGE_FALLBACK";

        return RecognitionEngineResult.builder()
                .recognitionMode(mode)
                .candidates(candidates)
                .build();
    }

    private List<RecognitionEngineCandidate> recognizeByFilename(String fileName, int topK) {
        LinkedHashMap<String, RecognitionEngineCandidate> results = new LinkedHashMap<>();
        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, String> entry : FILENAME_HINTS.entrySet()) {
            if (!normalizedFileName.contains(entry.getKey())) {
                continue;
            }

            if (results.containsKey(entry.getValue())) {
                continue;
            }

            BigDecimal confidence = scoreByIndex(results.size(), new BigDecimal("0.94"), new BigDecimal("0.08"));
            results.put(entry.getValue(), RecognitionEngineCandidate.builder()
                    .label(entry.getValue())
                    .canonicalLabel(entry.getValue())
                    .confidence(confidence)
                    .matchReason("mock filename hint")
                    .source("mock-engine")
                    .build());

            if (results.size() >= topK) {
                break;
            }
        }

        return new ArrayList<>(results.values());
    }

    private List<RecognitionEngineCandidate> recognizeByDigest(MultipartFile file,
                                                               int topK,
                                                               int existingCount) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded image", ex);
        }

        int offset = Math.floorMod(computeDigest(bytes), LABEL_POOL.size());
        List<RecognitionEngineCandidate> results = new ArrayList<>();

        for (int index = 0; index < LABEL_POOL.size() && existingCount + results.size() < topK; index++) {
            String label = LABEL_POOL.get((offset + index) % LABEL_POOL.size());
            if (results.stream().anyMatch(item -> label.equals(item.getLabel()))) {
                continue;
            }

            BigDecimal confidence = scoreByIndex(existingCount + results.size(),
                    new BigDecimal("0.76"),
                    new BigDecimal("0.07"));
            results.add(RecognitionEngineCandidate.builder()
                    .label(label)
                    .canonicalLabel(label)
                    .confidence(confidence)
                    .matchReason("mock digest fallback")
                    .source("mock-engine")
                    .build());
        }

        return results;
    }

    private int computeDigest(byte[] bytes) {
        int hash = 17;
        for (byte value : bytes) {
            hash = 31 * hash + value;
        }
        return hash;
    }

    private BigDecimal scoreByIndex(int index, BigDecimal start, BigDecimal step) {
        BigDecimal value = start.subtract(step.multiply(BigDecimal.valueOf(index)));
        if (value.compareTo(new BigDecimal("0.35")) < 0) {
            value = new BigDecimal("0.35");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, String> createFilenameHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put("rice", "米饭");
        hints.put("noodle", "面条");
        hints.put("egg", "鸡蛋");
        hints.put("milk", "牛奶");
        hints.put("bread", "面包");
        hints.put("apple", "苹果");
        hints.put("banana", "香蕉");
        hints.put("salad", "沙拉");
        hints.put("chicken", "鸡肉");
        hints.put("beef", "牛肉");
        hints.put("fish", "鱼");
        hints.put("shrimp", "虾");
        hints.put("yogurt", "酸奶");
        hints.put("broccoli", "西兰花");
        hints.put(new String("米饭".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "米饭");
        hints.put(new String("鸡蛋".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "鸡蛋");
        hints.put(new String("面".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "面条");
        hints.put(new String("牛奶".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "牛奶");
        hints.put(new String("苹果".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "苹果");
        hints.put(new String("香蕉".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "香蕉");
        hints.put(new String("沙拉".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "沙拉");
        return hints;
    }
}
