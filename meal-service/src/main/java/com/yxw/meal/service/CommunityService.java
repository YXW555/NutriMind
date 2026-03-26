package com.yxw.meal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.CommunityCommentRequest;
import com.yxw.meal.dto.CommunityCommentResponse;
import com.yxw.meal.dto.CommunityPostRequest;
import com.yxw.meal.dto.CommunityPostResponse;
import com.yxw.meal.entity.CommunityComment;
import com.yxw.meal.entity.CommunityPost;
import com.yxw.meal.entity.CommunityPostLike;
import com.yxw.meal.entity.PostFavorite;
import com.yxw.meal.mapper.CommunityCommentMapper;
import com.yxw.meal.mapper.CommunityPostLikeMapper;
import com.yxw.meal.mapper.CommunityPostMapper;
import com.yxw.meal.mapper.PostFavoriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final CommunityPostMapper communityPostMapper;
    private final CommunityPostLikeMapper communityPostLikeMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final ObjectMapper objectMapper;

    public CommunityService(CommunityPostMapper communityPostMapper,
                            CommunityPostLikeMapper communityPostLikeMapper,
                            CommunityCommentMapper communityCommentMapper,
                            PostFavoriteMapper postFavoriteMapper,
                            ObjectMapper objectMapper) {
        this.communityPostMapper = communityPostMapper;
        this.communityPostLikeMapper = communityPostLikeMapper;
        this.communityCommentMapper = communityCommentMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<CommunityPostResponse> listPosts(Long userId, String keyword, String tag, long current, long size) {
        Page<CommunityPost> page = new Page<>(current, size);
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<CommunityPost>()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(CommunityPost::getTitle, keyword)
                        .or()
                        .like(CommunityPost::getContent, keyword)
                        .or()
                        .like(CommunityPost::getAuthorName, keyword))
                .eq(StringUtils.hasText(tag) && !"全部".equals(tag), CommunityPost::getTag, tag)
                .orderByDesc(CommunityPost::getCreatedAt)
                .orderByDesc(CommunityPost::getId);

        Page<CommunityPost> result = communityPostMapper.selectPage(page, wrapper);
        List<CommunityPost> records = result.getRecords();
        Set<Long> likedPostIds = resolveLikedPostIds(userId, records);
        Set<Long> favoritedPostIds = resolveFavoritedPostIds(userId, records);
        List<CommunityPostResponse> responses = records.stream()
                .map(post -> toResponse(post, likedPostIds.contains(post.getId()), favoritedPostIds.contains(post.getId())))
                .toList();
        return new PageResponse<>(responses, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CommunityPostResponse getPost(Long userId, Long postId) {
        CommunityPost post = requirePost(postId);
        return toResponse(post, hasLiked(userId, postId), hasFavorited(userId, postId));
    }

    @Transactional
    public CommunityPostResponse createPost(Long userId, CommunityPostRequest request) {
        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setAuthorName(resolveAuthorName(request.getAuthorName()));
        post.setTitle(trimToNull(request.getTitle()));
        post.setContent(request.getContent().trim());
        post.setTag(normalizeTag(request.getTag()));
        post.setImageUrls(serializeImageUrls(normalizeImageUrls(request.getImageUrls())));
        post.setLikeCount(0);
        post.setFavoriteCount(0);
        post.setCommentCount(0);
        communityPostMapper.insert(post);
        return getPost(userId, post.getId());
    }

    @Transactional
    public CommunityPostResponse toggleLike(Long userId, Long postId) {
        CommunityPost post = requirePost(postId);
        CommunityPostLike existing = communityPostLikeMapper.selectOne(new LambdaQueryWrapper<CommunityPostLike>()
                .eq(CommunityPostLike::getPostId, postId)
                .eq(CommunityPostLike::getUserId, userId));

        boolean liked;
        int likeCount = zeroSafe(post.getLikeCount());
        if (existing == null) {
            CommunityPostLike like = new CommunityPostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            communityPostLikeMapper.insert(like);
            post.setLikeCount(likeCount + 1);
            liked = true;
        } else {
            communityPostLikeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, likeCount - 1));
            liked = false;
        }
        communityPostMapper.updateById(post);
        return toResponse(post, liked, hasFavorited(userId, postId));
    }

    @Transactional
    public CommunityPostResponse toggleFavorite(Long userId, Long postId) {
        CommunityPost post = requirePost(postId);
        PostFavorite existing = postFavoriteMapper.selectOne(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getPostId, postId)
                .eq(PostFavorite::getUserId, userId));

        boolean favorited;
        int favoriteCount = zeroSafe(post.getFavoriteCount());
        if (existing == null) {
            PostFavorite favorite = new PostFavorite();
            favorite.setPostId(postId);
            favorite.setUserId(userId);
            postFavoriteMapper.insert(favorite);
            post.setFavoriteCount(favoriteCount + 1);
            favorited = true;
        } else {
            postFavoriteMapper.deleteById(existing.getId());
            post.setFavoriteCount(Math.max(0, favoriteCount - 1));
            favorited = false;
        }
        communityPostMapper.updateById(post);
        return toResponse(post, hasLiked(userId, postId), favorited);
    }

    public List<CommunityCommentResponse> listComments(Long userId, Long postId) {
        requirePost(postId);
        return communityCommentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getPostId, postId)
                        .orderByAsc(CommunityComment::getCreatedAt)
                        .orderByAsc(CommunityComment::getId))
                .stream()
                .map(comment -> toCommentResponse(userId, comment))
                .toList();
    }

    @Transactional
    public CommunityCommentResponse createComment(Long userId, Long postId, CommunityCommentRequest request) {
        CommunityPost post = requirePost(postId);
        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setAuthorName(SecurityContextUtils.currentUsername().orElse("NutriMind User"));
        comment.setContent(request.getContent().trim());
        communityCommentMapper.insert(comment);

        post.setCommentCount(zeroSafe(post.getCommentCount()) + 1);
        communityPostMapper.updateById(post);
        return toCommentResponse(userId, communityCommentMapper.selectById(comment.getId()));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        CommunityComment comment = communityCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("comment not found");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new IllegalArgumentException("cross-user access is not allowed");
        }
        communityCommentMapper.deleteById(commentId);

        CommunityPost post = communityPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, zeroSafe(post.getCommentCount()) - 1));
            communityPostMapper.updateById(post);
        }
    }

    public long countPosts() {
        Long count = communityPostMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    public void saveAll(List<CommunityPost> posts) {
        for (CommunityPost post : posts) {
            communityPostMapper.insert(post);
        }
    }

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        return post;
    }

    private boolean hasLiked(Long userId, Long postId) {
        if (userId == null) {
            return false;
        }
        return communityPostLikeMapper.selectCount(new LambdaQueryWrapper<CommunityPostLike>()
                .eq(CommunityPostLike::getUserId, userId)
                .eq(CommunityPostLike::getPostId, postId)) > 0;
    }

    private boolean hasFavorited(Long userId, Long postId) {
        if (userId == null) {
            return false;
        }
        return postFavoriteMapper.selectCount(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId)) > 0;
    }

    private Set<Long> resolveLikedPostIds(Long userId, List<CommunityPost> posts) {
        if (userId == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
        return communityPostLikeMapper.selectList(new LambdaQueryWrapper<CommunityPostLike>()
                        .eq(CommunityPostLike::getUserId, userId)
                        .in(CommunityPostLike::getPostId, postIds))
                .stream()
                .map(CommunityPostLike::getPostId)
                .collect(Collectors.toSet());
    }

    private Set<Long> resolveFavoritedPostIds(Long userId, List<CommunityPost> posts) {
        if (userId == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
        return postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .in(PostFavorite::getPostId, postIds))
                .stream()
                .map(PostFavorite::getPostId)
                .collect(Collectors.toSet());
    }

    private String resolveAuthorName(String authorName) {
        if (StringUtils.hasText(authorName)) {
            return authorName.trim();
        }
        return SecurityContextUtils.currentUsername().orElse("NutriMind User");
    }

    private String normalizeTag(String tag) {
        return StringUtils.hasText(tag) ? tag.trim() : "全部";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        List<String> normalized = imageUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > 3) {
            throw new IllegalArgumentException("you can upload at most 3 images");
        }
        boolean invalidUrl = normalized.stream().anyMatch(url -> !url.startsWith("/api/community/media/"));
        if (invalidUrl) {
            throw new IllegalArgumentException("invalid image url");
        }
        return normalized;
    }

    private String serializeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize image urls");
        }
    }

    private List<String> deserializeImageUrls(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imageUrls, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private CommunityPostResponse toResponse(CommunityPost post, boolean liked, boolean favorited) {
        return CommunityPostResponse.builder()
                .id(post.getId())
                .authorName(post.getAuthorName())
                .title(post.getTitle())
                .content(post.getContent())
                .tag(post.getTag())
                .imageUrls(deserializeImageUrls(post.getImageUrls()))
                .likeCount(zeroSafe(post.getLikeCount()))
                .liked(liked)
                .favoriteCount(zeroSafe(post.getFavoriteCount()))
                .favorited(favorited)
                .commentCount(zeroSafe(post.getCommentCount()))
                .createdAt(post.getCreatedAt())
                .build();
    }

    private CommunityCommentResponse toCommentResponse(Long userId, CommunityComment comment) {
        return CommunityCommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .authorName(comment.getAuthorName())
                .content(comment.getContent())
                .ownComment(userId != null && userId.equals(comment.getUserId()))
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private int zeroSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
