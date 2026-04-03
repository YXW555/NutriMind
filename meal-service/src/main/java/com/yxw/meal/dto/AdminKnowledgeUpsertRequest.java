package com.yxw.meal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminKnowledgeUpsertRequest {

    private String id;

    private String fileName;

    @NotBlank(message = "title must not be blank")
    private String title;

    @NotBlank(message = "authority must not be blank")
    private String authority;

    @NotBlank(message = "source name must not be blank")
    private String sourceName;

    private String sourceUrl;

    private String tag;

    private String status;

    @NotBlank(message = "excerpt must not be blank")
    private String excerpt;

    @NotBlank(message = "content must not be blank")
    private String content;
}
