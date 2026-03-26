package com.yxw.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonInferenceResponse {

    private String mode;

    @JsonProperty("model_version")
    private String modelVersion;

    private List<PythonInferencePrediction> predictions;
}
