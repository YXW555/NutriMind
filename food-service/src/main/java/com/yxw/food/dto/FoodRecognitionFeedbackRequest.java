package com.yxw.food.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FoodRecognitionFeedbackRequest {

    private Long foodId;

    private String matchedFoodName;

    private String recognizedLabel;

    private String recognizedCanonicalLabel;

    private BigDecimal confidence;

    private String recognitionMode;

    private List<String> searchTerms;

    private Boolean manualConfirmationRequired = Boolean.TRUE;
}
