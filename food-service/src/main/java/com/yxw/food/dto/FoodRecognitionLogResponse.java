package com.yxw.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodRecognitionLogResponse {

    private Long id;

    private Long userId;

    private Long foodId;

    private String matchedFoodName;

    private String recognizedLabel;

    private String recognizedCanonicalLabel;

    private BigDecimal confidence;

    private String recognitionMode;

    private String searchTerms;

    private Boolean manualConfirmationRequired;

    private LocalDateTime createdAt;
}
