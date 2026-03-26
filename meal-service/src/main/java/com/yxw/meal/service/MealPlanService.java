package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.meal.client.FoodCatalogClient;
import com.yxw.meal.dto.CreateMealRequest;
import com.yxw.meal.dto.MealDetailCommand;
import com.yxw.meal.dto.MealPlanApplyRequest;
import com.yxw.meal.dto.MealPlanDaySummaryResponse;
import com.yxw.meal.dto.MealPlanItemCommand;
import com.yxw.meal.dto.MealPlanItemResponse;
import com.yxw.meal.dto.MealPlanResponse;
import com.yxw.meal.dto.MealPlanSaveRequest;
import com.yxw.meal.dto.MealRecordResponse;
import com.yxw.meal.entity.MealDetail;
import com.yxw.meal.entity.MealPlan;
import com.yxw.meal.entity.MealPlanItem;
import com.yxw.meal.entity.MealRecord;
import com.yxw.meal.mapper.MealPlanItemMapper;
import com.yxw.meal.mapper.MealPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MealPlanService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final FoodCatalogClient foodCatalogClient;
    private final MealRecordService mealRecordService;
    private final MealDetailService mealDetailService;

    public MealPlanService(MealPlanMapper mealPlanMapper,
                           MealPlanItemMapper mealPlanItemMapper,
                           FoodCatalogClient foodCatalogClient,
                           MealRecordService mealRecordService,
                           MealDetailService mealDetailService) {
        this.mealPlanMapper = mealPlanMapper;
        this.mealPlanItemMapper = mealPlanItemMapper;
        this.foodCatalogClient = foodCatalogClient;
        this.mealRecordService = mealRecordService;
        this.mealDetailService = mealDetailService;
    }

    public MealPlanResponse getDailyPlan(Long userId, LocalDate planDate) {
        MealPlan plan = findPlan(userId, planDate);
        if (plan == null) {
            return emptyPlan(planDate);
        }
        return buildPlanResponse(plan);
    }

    public List<MealPlanDaySummaryResponse> listWeekPlans(Long userId, LocalDate anchorDate) {
        LocalDate startDate = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(6);
        Map<LocalDate, MealPlan> planMap = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                        .eq(MealPlan::getUserId, userId)
                        .between(MealPlan::getPlanDate, startDate, endDate))
                .stream()
                .collect(HashMap::new, (map, plan) -> map.put(plan.getPlanDate(), plan), HashMap::putAll);

        List<MealPlanDaySummaryResponse> summaries = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            MealPlan plan = planMap.get(cursor);
            if (plan == null) {
                summaries.add(MealPlanDaySummaryResponse.builder()
                        .planDate(cursor)
                        .status("DRAFT")
                        .itemCount(0)
                        .totalCalories(BigDecimal.ZERO)
                        .hasPlan(false)
                        .build());
            } else {
                long itemCount = mealPlanItemMapper.selectCount(new LambdaQueryWrapper<MealPlanItem>()
                        .eq(MealPlanItem::getPlanId, plan.getId()));
                summaries.add(MealPlanDaySummaryResponse.builder()
                        .planDate(plan.getPlanDate())
                        .title(plan.getTitle())
                        .status(plan.getStatus())
                        .itemCount((int) itemCount)
                        .totalCalories(zeroSafe(plan.getTotalCalories()))
                        .hasPlan(true)
                        .build());
            }
            cursor = cursor.plusDays(1);
        }
        return summaries;
    }

    @Transactional
    public MealPlanResponse saveDailyPlan(Long userId, MealPlanSaveRequest request) {
        MealPlan plan = findPlan(userId, request.getPlanDate());
        if (plan == null) {
            plan = new MealPlan();
            plan.setUserId(userId);
            plan.setPlanDate(request.getPlanDate());
            plan.setStatus("DRAFT");
            plan.setTotalCalories(BigDecimal.ZERO);
            plan.setTotalProtein(BigDecimal.ZERO);
            plan.setTotalFat(BigDecimal.ZERO);
            plan.setTotalCarbohydrate(BigDecimal.ZERO);
            mealPlanMapper.insert(plan);
        }

        List<MealPlanItemCommand> commands = request.getItems() == null ? Collections.emptyList() : request.getItems();
        mealPlanItemMapper.delete(new LambdaQueryWrapper<MealPlanItem>()
                .eq(MealPlanItem::getPlanId, plan.getId()));

        List<MealPlanItem> items = buildItems(plan.getId(), commands);
        for (MealPlanItem item : items) {
            mealPlanItemMapper.insert(item);
        }

        plan.setTitle(trimToNull(request.getTitle()));
        plan.setNotes(trimToNull(request.getNotes()));
        plan.setStatus(items.isEmpty() ? "DRAFT" : "READY");
        plan.setTotalCalories(sum(items, MealPlanItem::getCalories));
        plan.setTotalProtein(sum(items, MealPlanItem::getProtein));
        plan.setTotalFat(sum(items, MealPlanItem::getFat));
        plan.setTotalCarbohydrate(sum(items, MealPlanItem::getCarbohydrate));
        mealPlanMapper.updateById(plan);
        return buildPlanResponse(mealPlanMapper.selectById(plan.getId()));
    }

    @Transactional
    public MealRecordResponse applyDailyPlan(Long userId, MealPlanApplyRequest request) {
        MealPlan plan = findPlan(userId, request.getPlanDate());
        if (plan == null) {
            throw new IllegalArgumentException("meal plan not found");
        }

        List<MealPlanItem> items = listItems(plan.getId());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("meal plan is empty");
        }

        MealRecord existingRecord = mealRecordService.lambdaQuery()
                .eq(MealRecord::getUserId, userId)
                .eq(MealRecord::getRecordDate, request.getPlanDate())
                .one();
        if (existingRecord != null) {
            long detailCount = mealDetailService.lambdaQuery()
                    .eq(MealDetail::getRecordId, existingRecord.getId())
                    .count();
            if (detailCount > 0) {
                throw new IllegalArgumentException("meal plan can only be applied to an empty date");
            }
        }

        CreateMealRequest createMealRequest = new CreateMealRequest();
        createMealRequest.setRecordDate(request.getPlanDate());
        createMealRequest.setDetails(items.stream().map(item -> {
            MealDetailCommand command = new MealDetailCommand();
            command.setFoodId(item.getFoodId());
            command.setQuantity(item.getQuantity());
            command.setMealType(item.getMealType());
            return command;
        }).toList());

        MealRecordResponse response = mealRecordService.createMeal(userId, createMealRequest);
        plan.setStatus("APPLIED");
        mealPlanMapper.updateById(plan);
        return response;
    }

    private MealPlan findPlan(Long userId, LocalDate planDate) {
        return mealPlanMapper.selectOne(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getUserId, userId)
                .eq(MealPlan::getPlanDate, planDate));
    }

    private List<MealPlanItem> buildItems(Long planId, List<MealPlanItemCommand> commands) {
        List<MealPlanItem> items = new ArrayList<>();
        int fallbackSortOrder = 0;
        for (MealPlanItemCommand command : commands) {
            FoodNutritionSnapshot food = foodCatalogClient.getFoodById(command.getFoodId());
            BigDecimal ratio = command.getQuantity().divide(HUNDRED, 4, RoundingMode.HALF_UP);
            MealPlanItem item = new MealPlanItem();
            item.setPlanId(planId);
            item.setFoodId(command.getFoodId());
            item.setMealType(resolveMealType(command.getMealType()));
            item.setQuantity(command.getQuantity());
            item.setNote(trimToNull(command.getNote()));
            item.setSortOrder(command.getSortOrder() == null ? fallbackSortOrder++ : command.getSortOrder());
            item.setCalories(multiply(food.getCalories(), ratio));
            item.setProtein(multiply(food.getProtein(), ratio));
            item.setFat(multiply(food.getFat(), ratio));
            item.setCarbohydrate(multiply(food.getCarbohydrate(), ratio));
            items.add(item);
        }
        return items;
    }

    private MealPlanResponse buildPlanResponse(MealPlan plan) {
        List<MealPlanItem> items = listItems(plan.getId());
        Map<Long, String> foodNames = new HashMap<>();
        return MealPlanResponse.builder()
                .id(plan.getId())
                .planDate(plan.getPlanDate())
                .title(plan.getTitle())
                .notes(plan.getNotes())
                .status(plan.getStatus())
                .totalCalories(zeroSafe(plan.getTotalCalories()))
                .totalProtein(zeroSafe(plan.getTotalProtein()))
                .totalFat(zeroSafe(plan.getTotalFat()))
                .totalCarbohydrate(zeroSafe(plan.getTotalCarbohydrate()))
                .items(items.stream().map(item -> MealPlanItemResponse.builder()
                        .id(item.getId())
                        .foodId(item.getFoodId())
                        .foodName(foodNames.computeIfAbsent(item.getFoodId(), this::fetchFoodName))
                        .mealType(item.getMealType())
                        .quantity(zeroSafe(item.getQuantity()))
                        .note(item.getNote())
                        .sortOrder(item.getSortOrder())
                        .calories(zeroSafe(item.getCalories()))
                        .protein(zeroSafe(item.getProtein()))
                        .fat(zeroSafe(item.getFat()))
                        .carbohydrate(zeroSafe(item.getCarbohydrate()))
                        .build()).toList())
                .build();
    }

    private MealPlanResponse emptyPlan(LocalDate planDate) {
        return MealPlanResponse.builder()
                .planDate(planDate)
                .status("DRAFT")
                .totalCalories(BigDecimal.ZERO)
                .totalProtein(BigDecimal.ZERO)
                .totalFat(BigDecimal.ZERO)
                .totalCarbohydrate(BigDecimal.ZERO)
                .items(List.of())
                .build();
    }

    private List<MealPlanItem> listItems(Long planId) {
        return mealPlanItemMapper.selectList(new LambdaQueryWrapper<MealPlanItem>()
                .eq(MealPlanItem::getPlanId, planId)
                .orderByAsc(MealPlanItem::getSortOrder)
                .orderByAsc(MealPlanItem::getId));
    }

    private String fetchFoodName(Long foodId) {
        return foodCatalogClient.getFoodById(foodId).getName();
    }

    private String resolveMealType(String mealType) {
        if (!StringUtils.hasText(mealType)) {
            return "SNACK";
        }
        return mealType.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        BigDecimal source = value == null ? BigDecimal.ZERO : value;
        return source.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<MealPlanItem> items, java.util.function.Function<MealPlanItem, BigDecimal> mapper) {
        return items.stream()
                .map(mapper)
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
