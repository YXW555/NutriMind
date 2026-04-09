package com.yxw.meal.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.PageResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.meal.dto.CommunityCommentRequest;
import com.yxw.meal.dto.CommunityCommentResponse;
import com.yxw.meal.dto.CommunityImageUploadResponse;
import com.yxw.meal.dto.CommunityPostRequest;
import com.yxw.meal.dto.CommunityPostResponse;
import com.yxw.meal.service.CommunityMediaService;
import com.yxw.meal.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/community")
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityMediaService communityMediaService;

    public CommunityController(CommunityService communityService, CommunityMediaService communityMediaService) {
        this.communityService = communityService;
        this.communityMediaService = communityMediaService;
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<CommunityPostResponse>> listPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(communityService.listPosts(currentUserId, keyword, tag, current, size));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<CommunityPostResponse> getPost(@PathVariable Long postId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(communityService.getPost(currentUserId, postId));
    }

    @PostMapping("/posts")
    public ApiResponse<CommunityPostResponse> createPost(@Valid @RequestBody CommunityPostRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("post created", communityService.createPost(currentUserId, request));
    }

    @GetMapping("/posts/mine")
    public ApiResponse<List<CommunityPostResponse>> listMyPosts() {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(communityService.listMyPosts(currentUserId));
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<CommunityPostResponse> updatePost(@PathVariable Long postId,
                                                         @Valid @RequestBody CommunityPostRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("post updated", communityService.updatePost(currentUserId, postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        communityService.deletePost(currentUserId, postId);
        return ApiResponse.success("post deleted", null);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CommunityImageUploadResponse> uploadImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            MultipartHttpServletRequest multipartRequest) {
        SecurityContextUtils.requireCurrentUserId();
        MultipartFile resolvedFile = resolveUploadFile(file, multipartRequest);
        String url = communityMediaService.storeImage(resolvedFile);
        return ApiResponse.success("image uploaded", CommunityImageUploadResponse.builder().url(url).build());
    }

    @GetMapping("/media/{filename:.+}")
    public ResponseEntity<Resource> loadImage(@PathVariable String filename) {
        Resource resource = communityMediaService.loadAsResource(filename);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(communityMediaService.resolveMediaType(filename))
                .body(resource);
    }

    @PostMapping("/posts/{postId}/like")
    public ApiResponse<CommunityPostResponse> toggleLike(@PathVariable Long postId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("like status updated", communityService.toggleLike(currentUserId, postId));
    }

    @PostMapping("/posts/{postId}/favorite")
    public ApiResponse<CommunityPostResponse> toggleFavorite(@PathVariable Long postId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("favorite status updated", communityService.toggleFavorite(currentUserId, postId));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommunityCommentResponse>> listComments(@PathVariable Long postId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(communityService.listComments(currentUserId, postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommunityCommentResponse> createComment(@PathVariable Long postId,
                                                               @Valid @RequestBody CommunityCommentRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("comment created", communityService.createComment(currentUserId, postId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        communityService.deleteComment(currentUserId, commentId);
        return ApiResponse.success("comment deleted", null);
    }

    private MultipartFile resolveUploadFile(MultipartFile file, MultipartHttpServletRequest multipartRequest) {
        if (file != null && !file.isEmpty()) {
            return file;
        }
        if (multipartRequest != null && !multipartRequest.getFileMap().isEmpty()) {
            return multipartRequest.getFileMap().values().iterator().next();
        }
        throw new IllegalArgumentException("no image selected");
    }
}
