package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostResponse {

    private Long id;

    private String authorName;

    private String title;

    private String content;

    private String tag;

    private String moderationStatus;

    private List<String> imageUrls;

    private Integer likeCount;

    private Boolean liked;

    private Integer favoriteCount;

    private Boolean favorited;

    private Integer commentCount;

    private LocalDateTime createdAt;
}
