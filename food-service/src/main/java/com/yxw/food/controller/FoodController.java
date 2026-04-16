package com.yxw.food.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.dto.FoodNutritionSnapshot;
import com.yxw.food.dto.FoodCategoryResponse;
import com.yxw.food.dto.FoodGraphOverviewResponse;
import com.yxw.food.dto.FoodGraphProfileResponse;
import com.yxw.food.dto.FoodGraphRelationResponse;
import com.yxw.food.dto.FoodGraphSyncResponse;
import com.yxw.food.dto.FoodMetadataResponse;
import com.yxw.food.dto.FoodRecognitionFeedbackRequest;
import com.yxw.food.dto.FoodRecognitionLogResponse;
import com.yxw.food.dto.FoodUpsertRequest;
import com.yxw.food.dto.KnowledgeSourceResponse;
import com.yxw.food.service.FoodBasicService;
import com.yxw.food.service.FoodGraphService;
import com.yxw.food.service.FoodMetadataService;
import com.yxw.food.service.FoodRecognitionFeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/foods")
public class FoodController {

    private final FoodBasicService foodBasicService;
    private final FoodGraphService foodGraphService;
    private final FoodMetadataService foodMetadataService;
    private final FoodRecognitionFeedbackService foodRecognitionFeedbackService;

    public FoodController(FoodBasicService foodBasicService,
                          FoodGraphService foodGraphService,
                          FoodMetadataService foodMetadataService,
                          FoodRecognitionFeedbackService foodRecognitionFeedbackService) {
        this.foodBasicService = foodBasicService;
        this.foodGraphService = foodGraphService;
        this.foodMetadataService = foodMetadataService;
        this.foodRecognitionFeedbackService = foodRecognitionFeedbackService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FoodNutritionSnapshot>> listFoods(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(foodBasicService.searchFoods(keyword, category, current, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<FoodNutritionSnapshot> getFood(@PathVariable Long id) {
        return ApiResponse.success(foodBasicService.getFood(id));
    }

    @GetMapping("/categories")
    public ApiResponse<List<FoodCategoryResponse>> listCategories() {
        return ApiResponse.success(foodMetadataService.listCategories());
    }

    @GetMapping("/{id}/metadata")
    public ApiResponse<FoodMetadataResponse> getFoodMetadata(@PathVariable Long id) {
        return ApiResponse.success(foodMetadataService.getFoodMetadata(id));
    }

    @GetMapping("/recognitions/logs")
    public ApiResponse<List<FoodRecognitionLogResponse>> listRecognitionLogs(
            @RequestParam(required = false) Long foodId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(foodMetadataService.listRecognitionLogs(foodId, size));
    }

    @GetMapping("/graph/overview")
    public ApiResponse<FoodGraphOverviewResponse> getGraphOverview() {
        return ApiResponse.success(foodGraphService.getOverview());
    }

    @GetMapping("/graph/relations")
    public ApiResponse<List<FoodGraphRelationResponse>> listGraphRelations(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String relationType,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(foodGraphService.listRelations(keyword, relationType, size));
    }

    @GetMapping("/{id}/graph")
    public ApiResponse<FoodGraphProfileResponse> getFoodGraphProfile(@PathVariable Long id,
                                                                     @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(foodGraphService.getFoodGraphProfile(id, size));
    }

    @GetMapping("/graph/sources")
    public ApiResponse<List<KnowledgeSourceResponse>> listKnowledgeSources() {
        return ApiResponse.success(foodGraphService.listKnowledgeSources());
    }

    @PostMapping("/graph/sync")
    public ApiResponse<FoodGraphSyncResponse> syncGraph() {
        return ApiResponse.success("图谱同步已触发", foodGraphService.syncGraph());
    }

    @PostMapping("/recognitions/feedback")
    public ApiResponse<Void> recordRecognitionFeedback(@RequestBody FoodRecognitionFeedbackRequest request) {
        foodRecognitionFeedbackService.recordFeedback(request);
        return ApiResponse.success("识别确认反馈已记录", null);
    }

    @PostMapping
    public ApiResponse<FoodNutritionSnapshot> createFood(@Valid @RequestBody FoodUpsertRequest request) {
        return ApiResponse.success("食物创建成功", foodBasicService.createFood(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FoodNutritionSnapshot> updateFood(@PathVariable Long id,
                                                         @Valid @RequestBody FoodUpsertRequest request) {
        return ApiResponse.success("食物更新成功", foodBasicService.updateFood(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFood(@PathVariable Long id) {
        foodBasicService.deleteFood(id);
        return ApiResponse.success("食物删除成功", null);
    }
}
