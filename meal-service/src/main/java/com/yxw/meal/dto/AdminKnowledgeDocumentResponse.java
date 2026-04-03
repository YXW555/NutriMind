package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminKnowledgeDocumentResponse {

    private String id;

    private String fileName;

    private String title;

    private String authority;

    private String sourceName;

    private String sourceUrl;

    private String tag;

    private String status;

    private String excerpt;

    private String content;
}
