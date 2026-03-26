package com.yxw.meal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxw.meal.dto.CreateMealRequest;
import com.yxw.meal.dto.MealRecordResponse;
import com.yxw.meal.entity.MealRecord;

import java.time.LocalDate;

public interface MealRecordService extends IService<MealRecord> {

    MealRecordResponse createMeal(Long userId, CreateMealRequest request);

    MealRecordResponse getDailyMeal(Long userId, LocalDate recordDate);

    MealRecordResponse deleteMealDetail(Long userId, Long detailId);
}
