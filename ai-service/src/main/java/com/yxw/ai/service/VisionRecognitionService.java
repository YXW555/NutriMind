package com.yxw.ai.service;

import com.yxw.ai.config.VisionProperties;
import com.yxw.ai.dto.FoodRecognitionResponse;
import com.yxw.ai.dto.RecognitionCandidateResponse;
import com.yxw.ai.dto.RecognizedConceptResponse;
import com.yxw.ai.engine.FoodRecognitionEngine;
import com.yxw.ai.engine.RecognitionEngineCandidate;
import com.yxw.ai.engine.RecognitionEngineResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
        FoodCatalogMatchService.RecognitionCatalogResult catalogResult = foodCatalogMatchService.mapEngineCandidates(
                engineResult == null ? List.of() : engineResult.getCandidates(),
                topK
        );
        List<RecognitionCandidateResponse> candidates = catalogResult.candidates();

        if (candidates.isEmpty()) {
            throw new IllegalStateException("food recognition candidates unavailable");
        }

        return FoodRecognitionResponse.builder()
                .fileName(fileName)
                .fileSize(file.getSize())
                .recognitionMode(engineResult == null ? "UNKNOWN" : engineResult.getRecognitionMode())
                .topK(candidates.size())
                .recognizedConcept(buildRecognizedConcept(catalogResult.recognizedConcept(), engineResult))
                .candidates(candidates)
                .build();
    }

    private RecognizedConceptResponse buildRecognizedConcept(RecognizedConceptResponse baseConcept,
                                                             RecognitionEngineResult engineResult) {
        if (baseConcept == null && (engineResult == null
                || engineResult.getCandidates() == null
                || engineResult.getCandidates().isEmpty())) {
            return null;
        }

        RecognizedConceptResponse source = baseConcept;
        if (source == null) {
            RecognitionEngineCandidate candidate = engineResult.getCandidates().get(0);
            source = RecognizedConceptResponse.builder()
                    .displayName(firstNonBlank(candidate.getCanonicalLabel(), candidate.getLabel(), "识别食物"))
                    .rawLabel(candidate.getLabel())
                    .canonicalLabel(candidate.getCanonicalLabel())
                    .confidence(candidate.getConfidence() == null ? BigDecimal.ZERO : candidate.getConfidence())
                    .matchReason(candidate.getMatchReason())
                    .searchKeywords(candidate.getSearchKeywords() == null ? List.of() : candidate.getSearchKeywords())
                    .aliases(candidate.getAliases() == null ? List.of() : candidate.getAliases())
                    .generic(Boolean.FALSE)
                    .build();
        }

        return RecognizedConceptResponse.builder()
                .displayName(source.getDisplayName())
                .rawLabel(source.getRawLabel())
                .canonicalLabel(source.getCanonicalLabel())
                .confidence(source.getConfidence())
                .matchReason(source.getMatchReason())
                .searchKeywords(source.getSearchKeywords() == null ? List.of() : source.getSearchKeywords())
                .aliases(source.getAliases() == null ? List.of() : source.getAliases())
                .generic(source.getGeneric())
                .cookingMethod(engineResult == null ? null : engineResult.getCookingMethod())
                .estimatedWeightGrams(engineResult == null ? null : engineResult.getEstimatedWeightGrams())
                .estimatedWeightMinGrams(engineResult == null ? null : engineResult.getEstimatedWeightMinGrams())
                .estimatedWeightMaxGrams(engineResult == null ? null : engineResult.getEstimatedWeightMaxGrams())
                .portionDescription(engineResult == null ? null : engineResult.getPortionDescription())
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
}
