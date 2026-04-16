package com.yxw.user.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.user.dto.HealthGoalRequest;
import com.yxw.user.dto.HealthGoalResponse;
import com.yxw.user.dto.HealthProfileRequest;
import com.yxw.user.dto.HealthProfileResponse;
import com.yxw.user.dto.AccountInfoRequest;
import com.yxw.user.dto.AvatarUploadResponse;
import com.yxw.user.dto.ProfileOverviewResponse;
import com.yxw.user.dto.WeightLogRequest;
import com.yxw.user.dto.WeightLogResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.yxw.user.service.ProfileService;
import com.yxw.user.service.ProfileAvatarMediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileAvatarMediaService profileAvatarMediaService;

    public ProfileController(ProfileService profileService,
                             ProfileAvatarMediaService profileAvatarMediaService) {
        this.profileService = profileService;
        this.profileAvatarMediaService = profileAvatarMediaService;
    }

    @GetMapping("/overview")
    public ApiResponse<ProfileOverviewResponse> getOverview() {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(profileService.getOverview(currentUserId));
    }

    @PutMapping("/info")
    public ApiResponse<ProfileOverviewResponse> saveAccountInfo(@RequestBody AccountInfoRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("profile updated", profileService.saveAccountInfo(currentUserId, request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        String avatarUrl = profileAvatarMediaService.storeAvatar(file);
        profileService.saveAvatar(currentUserId, avatarUrl);
        return ApiResponse.success("avatar uploaded", AvatarUploadResponse.builder().avatarUrl(avatarUrl).build());
    }

    @GetMapping("/avatar/{filename:.+}")
    public ResponseEntity<Resource> loadAvatar(@PathVariable String filename) {
        Resource resource = profileAvatarMediaService.loadAsResource(filename);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(profileAvatarMediaService.resolveMediaType(filename))
                .body(resource);
    }

    @PutMapping("/health")
    public ApiResponse<HealthProfileResponse> saveHealthProfile(@Valid @RequestBody HealthProfileRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("health profile saved", profileService.saveHealthProfile(currentUserId, request));
    }

    @PutMapping("/goal")
    public ApiResponse<HealthGoalResponse> saveHealthGoal(@Valid @RequestBody HealthGoalRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("health goal saved", profileService.saveHealthGoal(currentUserId, request));
    }

    @GetMapping("/weights")
    public ApiResponse<List<WeightLogResponse>> listWeightLogs(@RequestParam(defaultValue = "20") int limit) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success(profileService.listWeightLogs(currentUserId, limit));
    }

    @PostMapping("/weights")
    public ApiResponse<WeightLogResponse> saveWeightLog(@Valid @RequestBody WeightLogRequest request) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        return ApiResponse.success("weight log saved", profileService.saveWeightLog(currentUserId, request));
    }

    @DeleteMapping("/weights/{logId}")
    public ApiResponse<Void> deleteWeightLog(@PathVariable Long logId) {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        profileService.deleteWeightLog(currentUserId, logId);
        return ApiResponse.success("weight log deleted", null);
    }
}
