package com.yxw.food.service;

import com.yxw.food.dto.FoodRecognitionFeedbackRequest;

public interface FoodRecognitionFeedbackService {

    void recordFeedback(FoodRecognitionFeedbackRequest request);
}
