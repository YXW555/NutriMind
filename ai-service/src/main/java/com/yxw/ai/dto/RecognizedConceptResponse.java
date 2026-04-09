package com.yxw.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizedConceptResponse {

    private String displayName;

    private String rawLabel;

    private String canonicalLabel;

    private BigDecimal confidence;

    private String matchReason;

    @Builder.Default
    private List<String> searchKeywords = List.of();

    @Builder.Default
    private List<String> aliases = List.of();

    private Boolean generic;
}
