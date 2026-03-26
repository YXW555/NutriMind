package com.yxw.ai.engine;

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
public class RecognitionEngineCandidate {

    private String label;

    private String canonicalLabel;

    @Builder.Default
    private List<String> aliases = List.of();

    @Builder.Default
    private List<String> searchKeywords = List.of();

    private BigDecimal confidence;

    private String matchReason;

    private String source;
}
