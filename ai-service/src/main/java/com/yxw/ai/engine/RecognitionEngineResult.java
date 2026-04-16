package com.yxw.ai.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionEngineResult {

    private String recognitionMode;

    private List<RecognitionEngineCandidate> candidates;

    private String cookingMethod;

    private Integer estimatedWeightGrams;

    private Integer estimatedWeightMinGrams;

    private Integer estimatedWeightMaxGrams;

    private String portionDescription;
}
