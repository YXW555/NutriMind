package com.yxw.meal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.meal.client.FoodCatalogClient;
import com.yxw.meal.dto.CreateMealRequest;
import com.yxw.meal.dto.MealDetailCommand;
import com.yxw.meal.dto.MealDetailResponse;
import com.yxw.meal.dto.MealRecordResponse;
import com.yxw.meal.entity.MealDetail;
import com.yxw.meal.entity.MealRecord;
import com.yxw.meal.mapper.MealRecordMapper;
import com.yxw.meal.service.MealDetailService;
import com.yxw.meal.service.MealRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MealRecordServiceImpl extends ServiceImpl<MealRecordMapper, MealRecord> implements MealRecordService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MealDetailService mealDetailService;
    private final FoodCatalogClient foodCatalogClient;

    public MealRecordServiceImpl(MealDetailService mealDetailService, FoodCatalogClient foodCatalogClient) {
        this.mealDetailService = mealDetailService;
        this.foodCatalogClient = foodCatalogClient;
    }

    @Override
    public MealRecordResponse createMeal(Long userId, CreateMealRequest request) {
        MealRecord record = getOrCreateRecord(userId, request.getRecordDate());
        for (MealDetailCommand detailCommand : request.getDetails()) {
            FoodNutritionSnapshot food = foodCatalogClient.getFoodById(detailCommand.getFoodId());
            MealDetail detail = buildDetail(record.getId(), detailCommand, food);
            mealDetailService.save(detail);
        }
        return recalculateAndBuild(record);
    }

    @Override
    public MealRecordResponse getDailyMeal(Long userId, LocalDate recordDate) {
        MealRecord record = lambdaQuery()
                .eq(MealRecord::getUserId, userId)
                .eq(MealRecord::getRecordDate, recordDate)
                .one();
        if (record == null) {
            return MealRecordResponse.builder()
                    .userId(userId)
                    .recordDate(recordDate)
                    .totalCalories(BigDecimal.ZERO)
                    .totalProtein(BigDecimal.ZERO)
                    .totalFat(BigDecimal.ZERO)
                    .totalCarbohydrate(BigDecimal.ZERO)
                    .details(Collections.emptyList())
                    .build();
        }
        return buildRecordResponse(record);
    }

    @Override
    public MealRecordResponse deleteMealDetail(Long userId, Long detailId) {
        MealDetail detail = mealDetailService.getById(detailId);
        if (detail == null) {
            throw new IllegalArgumentException("meal detail not found: " + detailId);
        }
        MealRecord record = getById(detail.getRecordId());
        if (record == null) {
            throw new IllegalArgumentException("meal record not found: " + detail.getRecordId());
        }
        if (!record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("cross-user access is not allowed");
        }

        mealDetailService.removeById(detailId);
        List<MealDetail> remainingDetails = listDetails(record.getId());
        if (remainingDetails.isEmpty()) {
            removeById(record.getId());
            return MealRecordResponse.builder()
                    .userId(record.getUserId())
                    .recordDate(record.getRecordDate())
                    .totalCalories(BigDecimal.ZERO)
                    .totalProtein(BigDecimal.ZERO)
                    .totalFat(BigDecimal.ZERO)
                    .totalCarbohydrate(BigDecimal.ZERO)
                    .details(Collections.emptyList())
                    .build();
        }
        return recalculateAndBuild(record);
    }

    private MealRecord getOrCreateRecord(Long userId, LocalDate recordDate) {
        MealRecord record = lambdaQuery()
                .eq(MealRecord::getUserId, userId)
                .eq(MealRecord::getRecordDate, recordDate)
                .one();
        if (record != null) {
            return record;
        }
        MealRecord newRecord = new MealRecord();
        newRecord.setUserId(userId);
        newRecord.setRecordDate(recordDate);
        newRecord.setTotalCalories(BigDecimal.ZERO);
        newRecord.setTotalProtein(BigDecimal.ZERO);
        newRecord.setTotalFat(BigDecimal.ZERO);
        newRecord.setTotalCarbohydrate(BigDecimal.ZERO);
        save(newRecord);
        return newRecord;
    }

    private MealDetail buildDetail(Long recordId, MealDetailCommand command, FoodNutritionSnapshot food) {
        BigDecimal ratio = command.getQuantity().divide(HUNDRED, 4, RoundingMode.HALF_UP);
        MealDetail detail = new MealDetail();
        detail.setRecordId(recordId);
        detail.setFoodId(command.getFoodId());
        detail.setMealType(resolveMealType(command.getMealType()));
        detail.setQuantity(command.getQuantity());
        detail.setCalories(multiply(food.getCalories(), ratio));
        detail.setProtein(multiply(food.getProtein(), ratio));
        detail.setFat(multiply(food.getFat(), ratio));
        detail.setCarbohydrate(multiply(food.getCarbohydrate(), ratio));
        return detail;
    }

    private MealRecordResponse recalculateAndBuild(MealRecord record) {
        List<MealDetail> details = listDetails(record.getId());
        BigDecimal totalCalories = sum(details, MealDetail::getCalories);
        BigDecimal totalProtein = sum(details, MealDetail::getProtein);
        BigDecimal totalFat = sum(details, MealDetail::getFat);
        BigDecimal totalCarbohydrate = sum(details, MealDetail::getCarbohydrate);

        record.setTotalCalories(totalCalories);
        record.setTotalProtein(totalProtein);
        record.setTotalFat(totalFat);
        record.setTotalCarbohydrate(totalCarbohydrate);
        updateById(record);

        return MealRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .recordDate(record.getRecordDate())
                .totalCalories(totalCalories)
                .totalProtein(totalProtein)
                .totalFat(totalFat)
                .totalCarbohydrate(totalCarbohydrate)
                .details(toDetailResponses(details))
                .build();
    }

    private MealRecordResponse buildRecordResponse(MealRecord record) {
        return MealRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .recordDate(record.getRecordDate())
                .totalCalories(zeroSafe(record.getTotalCalories()))
                .totalProtein(zeroSafe(record.getTotalProtein()))
                .totalFat(zeroSafe(record.getTotalFat()))
                .totalCarbohydrate(zeroSafe(record.getTotalCarbohydrate()))
                .details(toDetailResponses(listDetails(record.getId())))
                .build();
    }

    private List<MealDetailResponse> toDetailResponses(List<MealDetail> details) {
        Map<Long, String> foodNames = new HashMap<>();
        return details.stream()
                .map(detail -> toDetailResponse(detail, foodNames.computeIfAbsent(detail.getFoodId(), this::fetchFoodName)))
                .toList();
    }

    private String fetchFoodName(Long foodId) {
        return foodCatalogClient.getFoodById(foodId).getName();
    }

    private List<MealDetail> listDetails(Long recordId) {
        return mealDetailService.lambdaQuery()
                .eq(MealDetail::getRecordId, recordId)
                .orderByDesc(MealDetail::getCreatedAt)
                .orderByDesc(MealDetail::getId)
                .list();
    }

    private BigDecimal sum(List<MealDetail> details, java.util.function.Function<MealDetail, BigDecimal> mapper) {
        return details.stream()
                .map(mapper)
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private MealDetailResponse toDetailResponse(MealDetail detail, String foodName) {
        return MealDetailResponse.builder()
                .id(detail.getId())
                .foodId(detail.getFoodId())
                .foodName(foodName)
                .mealType(detail.getMealType())
                .quantity(zeroSafe(detail.getQuantity()))
                .calories(zeroSafe(detail.getCalories()))
                .protein(zeroSafe(detail.getProtein()))
                .fat(zeroSafe(detail.getFat()))
                .carbohydrate(zeroSafe(detail.getCarbohydrate()))
                .createdAt(detail.getCreatedAt())
                .build();
    }

    private String resolveMealType(String mealType) {
        if (!StringUtils.hasText(mealType)) {
            return "SNACK";
        }
        return mealType.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        return source.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
