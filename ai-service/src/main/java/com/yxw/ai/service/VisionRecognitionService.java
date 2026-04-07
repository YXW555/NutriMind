package com.yxw.ai.service;

import com.yxw.ai.config.VisionProperties;
import com.yxw.ai.dto.FoodRecognitionResponse;
import com.yxw.ai.dto.RecognitionCandidateResponse;
import com.yxw.ai.engine.FoodRecognitionEngine;
import com.yxw.ai.engine.RecognitionEngineResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
public class VisionRecognitionService {

    private final FoodRecognitionEngine foodRecognitionEngine;
    private final FoodCatalogMatchService foodCatalogMatchService;
    private final VisionProperties visionProperties;

    public VisionRecognitionService(FoodRecognitionEngine foodRecognitionEngine,
                                    FoodCatalogMatchService foodCatalogMatchService,
                                    VisionProperties visionProperties) {
        this.foodRecognitionEngine = foodRecognitionEngine;
        this.foodCatalogMatchService = foodCatalogMatchService;
        this.visionProperties = visionProperties;
    }

    public FoodRecognitionResponse recognize(MultipartFile file, int requestedTopK) {
        validateImage(file);

        int topK = resolveTopK(requestedTopK);
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "uploaded-image";

        RecognitionEngineResult engineResult = foodRecognitionEngine.recognize(file, topK);
        List<RecognitionCandidateResponse> candidates = foodCatalogMatchService.mapEngineCandidates(
                engineResult == null ? List.of() : engineResult.getCandidates(),
                topK
        );

        if (candidates.isEmpty()) {
            throw new IllegalStateException("food recognition candidates unavailable");
        }

        return FoodRecognitionResponse.builder()
                .fileName(fileName)
                .fileSize(file.getSize())
                .recognitionMode(engineResult == null ? "UNKNOWN" : engineResult.getRecognitionMode())
                .topK(candidates.size())
                .candidates(candidates)
                .build();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("no image selected");
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("uploaded file must be an image");
        }
    }

    private int resolveTopK(int requestedTopK) {
        int topK = requestedTopK > 0 ? requestedTopK : visionProperties.getDefaultTopK();
        topK = Math.max(topK, 1);
        return Math.min(topK, Math.max(visionProperties.getMaxTopK(), 1));
    }
}
