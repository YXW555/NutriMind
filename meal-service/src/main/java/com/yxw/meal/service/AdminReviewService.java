package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.meal.dto.AdminReviewItemResponse;
import com.yxw.meal.entity.CommunityComment;
import com.yxw.meal.entity.CommunityPost;
import com.yxw.meal.mapper.CommunityCommentMapper;
import com.yxw.meal.mapper.CommunityPostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminReviewService {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityCommentMapper communityCommentMapper;

    public AdminReviewService(CommunityPostMapper communityPostMapper,
                              CommunityCommentMapper communityCommentMapper) {
        this.communityPostMapper = communityPostMapper;
        this.communityCommentMapper = communityCommentMapper;
    }

    public List<AdminReviewItemResponse> listItems() {
        List<AdminReviewItemResponse> items = new ArrayList<>();

        communityPostMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                        .orderByDesc(CommunityPost::getCreatedAt)
                        .orderByDesc(CommunityPost::getId)
                        .last("LIMIT 20"))
                .forEach(post -> items.add(toPostItem(post)));

        communityCommentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                        .orderByDesc(CommunityComment::getCreatedAt)
                        .orderByDesc(CommunityComment::getId)
                        .last("LIMIT 20"))
                .forEach(comment -> items.add(toCommentItem(comment)));

        return items.stream()
                .sorted(Comparator.comparing(AdminReviewItemResponse::getStatus)
                        .thenComparing(AdminReviewItemResponse::getId)
                        .reversed())
                .toList();
    }

    @Transactional
    public AdminReviewItemResponse updateStatus(String type, Long targetId, String status) {
        String normalizedType = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "";
        String moderationStatus = normalizeStatus(status);

        return switch (normalizedType) {
            case "POST" -> updatePostStatus(targetId, moderationStatus);
            case "COMMENT" -> updateCommentStatus(targetId, moderationStatus);
            default -> throw new IllegalArgumentException("unsupported review type");
        };
    }

    private AdminReviewItemResponse updatePostStatus(Long postId, String moderationStatus) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        post.setModerationStatus(moderationStatus);
        communityPostMapper.updateById(post);
        return toPostItem(post);
    }

    private AdminReviewItemResponse updateCommentStatus(Long commentId, String moderationStatus) {
        CommunityComment comment = communityCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("comment not found");
        }
        comment.setModerationStatus(moderationStatus);
        communityCommentMapper.updateById(comment);
        return toCommentItem(comment);
    }

    private AdminReviewItemResponse toPostItem(CommunityPost post) {
        RiskProfile riskProfile = evaluateRisk(post.getContent(), post.getImageUrls());
        return AdminReviewItemResponse.builder()
                .id("post-" + post.getId())
                .type("社区帖子")
                .targetId(String.valueOf(post.getId()))
                .author(post.getAuthorName())
                .risk(riskProfile.risk())
                .reason(riskProfile.reason())
                .status(toDisplayStatus(post.getModerationStatus()))
                .contentPreview(truncate(firstNonBlank(post.getTitle(), post.getContent()), 60))
                .build();
    }

    private AdminReviewItemResponse toCommentItem(CommunityComment comment) {
        RiskProfile riskProfile = evaluateRisk(comment.getContent(), null);
        return AdminReviewItemResponse.builder()
                .id("comment-" + comment.getId())
                .type("评论")
                .targetId(String.valueOf(comment.getId()))
                .author(comment.getAuthorName())
                .risk(riskProfile.risk())
                .reason(riskProfile.reason())
                .status(toDisplayStatus(comment.getModerationStatus()))
                .contentPreview(truncate(comment.getContent(), 60))
                .build();
    }

    private RiskProfile evaluateRisk(String content, String imageUrls) {
        String normalized = StringUtils.hasText(content) ? content.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("减肥药") || normalized.contains("绝食") || normalized.contains("辱骂")) {
            return new RiskProfile("高", "疑似包含高风险表达或不安全建议，建议优先人工复核。");
        }
        if (StringUtils.hasText(imageUrls)) {
            return new RiskProfile("中", "包含图片内容，建议核对图文是否一致。");
        }
        return new RiskProfile("低", "文本内容较为正常，可结合上下文快速审核。");
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "PENDING";
        }
        return switch (status.trim()) {
            case "已通过", "APPROVED" -> "APPROVED";
            case "已拦截", "REJECTED" -> "REJECTED";
            default -> "PENDING";
        };
    }

    private String toDisplayStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "待审核";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已拦截";
            default -> "待审核";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private record RiskProfile(String risk, String reason) {
    }
}
