package com.yxw.meal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.meal.client.FoodCatalogClient;
import com.yxw.meal.client.UserProfileClient;
import com.yxw.meal.client.dto.HealthGoalSnapshot;
import com.yxw.meal.client.dto.HealthProfileSnapshot;
import com.yxw.meal.client.dto.ProfileOverviewSnapshot;
import com.yxw.meal.config.RagProperties;
import com.yxw.meal.dto.GeneratedMealPlanResponse;
import com.yxw.meal.dto.GeneratedMealPlanWeekResponse;
import com.yxw.meal.dto.MealPlanGenerateDailyRequest;
import com.yxw.meal.dto.MealPlanGenerateWeekRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealPlanningAgentServiceTest {

    @Mock
    private QwenModelStudioService qwenModelStudioService;
    @Mock
    private MilvusKnowledgeStoreService milvusKnowledgeStoreService;
    @Mock
    private NutritionKnowledgeBaseService nutritionKnowledgeBaseService;
    @Mock
    private UserProfileClient userProfileClient;
    @Mock
    private FoodCatalogClient foodCatalogClient;
    @Mock
    private MealPlanService mealPlanService;

    private MealPlanningAgentService mealPlanningAgentService;
    private Map<String, FoodNutritionSnapshot> foodByName;
    private Map<Long, FoodNutritionSnapshot> foodById;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        ragProperties.getQwen().setApiKey("");

        mealPlanningAgentService = new MealPlanningAgentService(
                ragProperties,
                qwenModelStudioService,
                milvusKnowledgeStoreService,
                nutritionKnowledgeBaseService,
                userProfileClient,
                foodCatalogClient,
                mealPlanService,
                new ObjectMapper()
        );

        foodByName = new LinkedHashMap<>();
        foodById = new LinkedHashMap<>();
        seedFood("燕麦片", 1L, 389, 16.9, 6.9, 66.3);
        seedFood("全麦面包", 2L, 246, 12.0, 3.5, 41.0);
        seedFood("玉米", 3L, 106, 4.0, 1.2, 22.8);
        seedFood("鸡蛋", 4L, 144, 13.3, 8.8, 1.3);
        seedFood("酸奶", 5L, 72, 2.5, 2.7, 9.3);
        seedFood("牛奶", 6L, 62, 3.2, 3.5, 4.8);
        seedFood("鸡胸肉", 7L, 165, 31.0, 3.6, 0.0);
        seedFood("豆腐", 8L, 76, 8.0, 4.8, 1.9);
        seedFood("牛肉", 9L, 125, 20.3, 4.2, 0.0);
        seedFood("鱼", 10L, 123, 18.5, 5.0, 0.0);
        seedFood("米饭", 11L, 116, 2.6, 0.3, 25.9);
        seedFood("土豆", 12L, 81, 2.0, 0.2, 17.8);
        seedFood("西兰花", 13L, 34, 2.8, 0.4, 6.6);
        seedFood("黄瓜", 14L, 16, 0.8, 0.2, 2.9);
        seedFood("番茄", 15L, 20, 0.9, 0.2, 4.0);
        seedFood("沙拉", 16L, 120, 4.5, 6.0, 12.0);
        seedFood("香蕉", 17L, 93, 1.4, 0.2, 22.8);
        seedFood("苹果", 18L, 53, 0.3, 0.2, 13.5);
        seedFood("橙子", 19L, 48, 0.8, 0.2, 11.1);

        when(qwenModelStudioService.isReady()).thenReturn(false);
        when(milvusKnowledgeStoreService.isReady()).thenReturn(false);
        when(nutritionKnowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of(
                new NutritionKnowledgeBaseService.KnowledgeHit(
                        "k1",
                        "规律进餐与餐次安排",
                        "早餐与午餐更适合承担稳定供能任务",
                        "早餐与午餐应优先保证主食、优质蛋白和蔬果的基本组合。",
                        "早餐与午餐应优先保证主食、优质蛋白和蔬果的基本组合。",
                        "中国营养学会",
                        "《中国居民膳食指南（2022）》准则六",
                        "https://dg.cnsoc.org/article/2021b.html",
                        0.95D
                )
        ));
        when(foodCatalogClient.pickBestMatch(anyString())).thenAnswer(invocation -> foodByName.get(invocation.getArgument(0)));
        when(foodCatalogClient.getFoodById(anyLong())).thenAnswer(invocation -> foodById.get(invocation.getArgument(0)));
        when(userProfileClient.getOverview()).thenReturn(buildOverview());
    }

    @Test
    void shouldGenerateRuleBasedDailyPlanWhenQwenNotReady() {
        MealPlanGenerateDailyRequest request = new MealPlanGenerateDailyRequest();
        request.setPlanDate(LocalDate.of(2026, 4, 1));
        request.setPreference("训练日，晚餐清淡一点");

        Object result = mealPlanningAgentService.generateDaily(1L, request);

        assertThat(result).isInstanceOf(GeneratedMealPlanResponse.class);
        GeneratedMealPlanResponse response = (GeneratedMealPlanResponse) result;
        assertThat(response.getGenerationMode()).isEqualTo("RULE_BASED");
        assertThat(response.getItems()).isNotEmpty();
        assertThat(response.getItems().stream().map(item -> item.getMealType()).collect(Collectors.toSet()))
                .contains("BREAKFAST", "LUNCH", "DINNER");
        assertThat(response.getTotalCalories()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getSummary()).isNotBlank();
        assertThat(response.getReferences()).isNotEmpty();
    }

    @Test
    void shouldGenerateWeekPlanWithVisibleVariety() {
        MealPlanGenerateWeekRequest request = new MealPlanGenerateWeekRequest();
        request.setAnchorDate(LocalDate.of(2026, 4, 1));
        request.setPreference("工作日想省心一点");

        GeneratedMealPlanWeekResponse response = mealPlanningAgentService.generateWeek(1L, request);

        assertThat(response.getDays()).hasSize(7);
        assertThat(response.getGenerationMode()).isEqualTo("RULE_BASED");
        Set<String> foodNames = response.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .map(item -> item.getFoodName())
                .collect(Collectors.toSet());
        assertThat(foodNames.size()).isGreaterThan(8);
        assertThat(response.getSummary()).isNotBlank();
    }

    private void seedFood(String name, Long id, double calories, double protein, double fat, double carbohydrate) {
        FoodNutritionSnapshot snapshot = FoodNutritionSnapshot.builder()
                .id(id)
                .name(name)
                .category("demo")
                .unit("100克")
                .calories(BigDecimal.valueOf(calories))
                .protein(BigDecimal.valueOf(protein))
                .fat(BigDecimal.valueOf(fat))
                .carbohydrate(BigDecimal.valueOf(carbohydrate))
                .fiber(BigDecimal.ZERO)
                .status(1)
                .build();
        foodByName.put(name, snapshot);
        foodById.put(id, snapshot);
    }

    private ProfileOverviewSnapshot buildOverview() {
        HealthGoalSnapshot goal = new HealthGoalSnapshot();
        goal.setGoalType("FAT_LOSS");
        goal.setTargetCalories(BigDecimal.valueOf(1800));
        goal.setTargetProtein(BigDecimal.valueOf(110));

        HealthProfileSnapshot profile = new HealthProfileSnapshot();
        profile.setDietaryPreference("高蛋白");
        profile.setAllergies("无");

        ProfileOverviewSnapshot overview = new ProfileOverviewSnapshot();
        overview.setNickname("测试用户");
        overview.setUsername("tester");
        overview.setHealthGoal(goal);
        overview.setHealthProfile(profile);
        return overview;
    }
}
