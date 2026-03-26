package com.yxw.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonInferencePrediction {

    private String label;

    @JsonProperty("canonical_label")
    private String canonicalLabel;

    private BigDecimal confidence;

    @JsonProperty("match_reason")
    private String matchReason;

    private String source;

    private List<String> aliases;

    @JsonProperty("search_keywords")
    private List<String> searchKeywords;
}
