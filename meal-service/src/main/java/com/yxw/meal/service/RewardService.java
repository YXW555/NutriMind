package com.yxw.meal.service;

import com.yxw.meal.dto.RewardFeedbackResponse;
import com.yxw.meal.dto.RewardSummaryResponse;
import com.yxw.meal.entity.MealDetail;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface RewardService {

    RewardFeedbackResponse handleMealRecorded(Long userId,
                                              LocalDate recordDate,
                                              Set<String> beforeMealTypes,
                                              List<MealDetail> currentDetails);

    RewardSummaryResponse getSummary(Long userId);
}
