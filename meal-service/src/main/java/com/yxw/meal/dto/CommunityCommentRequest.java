package com.yxw.meal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommunityCommentRequest {

    @NotBlank(message = "comment content must not be blank")
    private String content;
}
