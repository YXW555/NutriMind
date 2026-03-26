package com.yxw.meal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommunityCommentResponse {

    private Long id;

    private Long postId;

    private Long userId;

    private String authorName;

    private String content;

    private Boolean ownComment;

    private LocalDateTime createdAt;
}
