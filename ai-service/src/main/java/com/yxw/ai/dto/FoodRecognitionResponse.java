package com.yxw.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodRecognitionResponse {

    private String fileName;

    private Long fileSize;

    private String recognitionMode;

    private Integer topK;

    private RecognizedConceptResponse recognizedConcept;

    private List<RecognitionCandidateResponse> candidates;
}
