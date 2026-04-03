package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.meal.dto.AdminKnowledgeDocumentResponse;
import com.yxw.meal.dto.AdminKnowledgeUpsertRequest;
import com.yxw.meal.service.AdminKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/knowledge")
public class AdminKnowledgeController {

    private final AdminKnowledgeService adminKnowledgeService;

    public AdminKnowledgeController(AdminKnowledgeService adminKnowledgeService) {
        this.adminKnowledgeService = adminKnowledgeService;
    }

    @GetMapping
    public ApiResponse<List<AdminKnowledgeDocumentResponse>> listDocuments() {
        return ApiResponse.success(adminKnowledgeService.listDocuments());
    }

    @PostMapping
    public ApiResponse<AdminKnowledgeDocumentResponse> createDocument(@Valid @RequestBody AdminKnowledgeUpsertRequest request) {
        return ApiResponse.success("knowledge saved", adminKnowledgeService.saveDocument(request));
    }

    @PutMapping
    public ApiResponse<AdminKnowledgeDocumentResponse> updateDocument(@Valid @RequestBody AdminKnowledgeUpsertRequest request) {
        return ApiResponse.success("knowledge saved", adminKnowledgeService.saveDocument(request));
    }

    @PostMapping("/reload")
    public ApiResponse<Void> reloadKnowledgeBase() {
        adminKnowledgeService.refreshKnowledgeBase();
        return ApiResponse.success("knowledge base reloaded", null);
    }
}
