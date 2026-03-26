package com.yxw.food.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.food.dto.FoodUpsertRequest;
import com.yxw.food.entity.FoodBasic;
import com.yxw.food.mapper.FoodBasicMapper;
import com.yxw.food.service.FoodBasicService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FoodBasicServiceImpl extends ServiceImpl<FoodBasicMapper, FoodBasic> implements FoodBasicService {

    @Override
    public PageResponse<FoodNutritionSnapshot> searchFoods(String keyword, String category, long current, long size) {
        Page<FoodBasic> page = lambdaQuery()
                .like(StringUtils.hasText(keyword), FoodBasic::getName, keyword)
                .eq(StringUtils.hasText(category), FoodBasic::getCategory, category)
                .orderByAsc(FoodBasic::getName)
                .page(new Page<>(current, size));

        List<FoodNutritionSnapshot> records = page.getRecords().stream()
                .map(this::toSnapshot)
                .toList();
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FoodNutritionSnapshot getFood(Long id) {
        FoodBasic food = requireFood(id);
        return toSnapshot(food);
    }

    @Override
    public FoodNutritionSnapshot createFood(FoodUpsertRequest request) {
        FoodBasic food = new FoodBasic();
        applyRequest(food, request);
        save(food);
        return toSnapshot(food);
    }

    @Override
    public FoodNutritionSnapshot updateFood(Long id, FoodUpsertRequest request) {
        FoodBasic food = requireFood(id);
        applyRequest(food, request);
        updateById(food);
        return toSnapshot(food);
    }

    @Override
    public void deleteFood(Long id) {
        removeById(id);
    }

    private FoodBasic requireFood(Long id) {
        FoodBasic food = getById(id);
        if (food == null) {
            throw new IllegalArgumentException("食物不存在: " + id);
        }
        return food;
    }

    private void applyRequest(FoodBasic food, FoodUpsertRequest request) {
        food.setName(request.getName());
        food.setCategory(request.getCategory());
        food.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : "100g");
        food.setCalories(defaultValue(request.getCalories()));
        food.setProtein(defaultValue(request.getProtein()));
        food.setFat(defaultValue(request.getFat()));
        food.setCarbohydrate(defaultValue(request.getCarbohydrate()));
        food.setFiber(defaultValue(request.getFiber()));
        food.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private FoodNutritionSnapshot toSnapshot(FoodBasic food) {
        return FoodNutritionSnapshot.builder()
                .id(food.getId())
                .name(food.getName())
                .category(food.getCategory())
                .unit(food.getUnit())
                .calories(defaultValue(food.getCalories()))
                .protein(defaultValue(food.getProtein()))
                .fat(defaultValue(food.getFat()))
                .carbohydrate(defaultValue(food.getCarbohydrate()))
                .fiber(defaultValue(food.getFiber()))
                .status(food.getStatus())
                .build();
    }
}
