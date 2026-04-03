package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_post")
public class CommunityPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String authorName;

    private String title;

    private String content;

    private String imageUrls;

    private String tag;

    private String moderationStatus;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
