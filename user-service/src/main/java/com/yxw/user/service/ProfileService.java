package com.yxw.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.user.dto.HealthGoalRequest;
import com.yxw.user.dto.HealthGoalResponse;
import com.yxw.user.dto.HealthProfileRequest;
import com.yxw.user.dto.HealthProfileResponse;
import com.yxw.user.dto.ProfileOverviewResponse;
import com.yxw.user.dto.WeightLogRequest;
import com.yxw.user.dto.WeightLogResponse;
import com.yxw.user.entity.HealthGoal;
import com.yxw.user.entity.UserAccount;
import com.yxw.user.entity.UserProfile;
import com.yxw.user.entity.WeightLog;
import com.yxw.user.mapper.HealthGoalMapper;
import com.yxw.user.mapper.UserProfileMapper;
import com.yxw.user.mapper.WeightLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProfileService {

    private final UserAccountService userAccountService;
    private final UserProfileMapper userProfileMapper;
    private final HealthGoalMapper healthGoalMapper;
    private final WeightLogMapper weightLogMapper;

    public ProfileService(UserAccountService userAccountService,
                          UserProfileMapper userProfileMapper,
                          HealthGoalMapper healthGoalMapper,
                          WeightLogMapper weightLogMapper) {
        this.userAccountService = userAccountService;
        this.userProfileMapper = userProfileMapper;
        this.healthGoalMapper = healthGoalMapper;
        this.weightLogMapper = weightLogMapper;
    }

    public ProfileOverviewResponse getOverview(Long userId) {
        UserAccount user = requireUser(userId);
        UserProfile profile = userProfileMapper.selectById(userId);
        HealthGoal goal = findGoal(userId);
        List<WeightLogResponse> logs = listWeightLogs(userId, 20);
        return ProfileOverviewResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .healthProfile(HealthProfileResponse.from(profile))
                .healthGoal(HealthGoalResponse.from(goal))
                .latestWeightKg(logs.isEmpty() ? null : logs.get(0).getWeightKg())
                .recentWeightLogs(logs)
                .build();
    }

    @Transactional
    public HealthProfileResponse saveHealthProfile(Long userId, HealthProfileRequest request) {
        requireUser(userId);
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
        }
        profile.setGender(trimToNull(request.getGender()));
        profile.setBirthDate(request.getBirthDate());
        profile.setHeightCm(request.getHeightCm());
        profile.setActivityLevel(trimToNull(request.getActivityLevel()));
        profile.setDietaryPreference(trimToNull(request.getDietaryPreference()));
        profile.setAllergies(trimToNull(request.getAllergies()));
        profile.setMedicalNotes(trimToNull(request.getMedicalNotes()));

        if (userProfileMapper.selectById(userId) == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return HealthProfileResponse.from(userProfileMapper.selectById(userId));
    }

    @Transactional
    public HealthGoalResponse saveHealthGoal(Long userId, HealthGoalRequest request) {
        requireUser(userId);
        HealthGoal goal = findGoal(userId);
        if (goal == null) {
            goal = new HealthGoal();
            goal.setUserId(userId);
        }
        goal.setGoalType(defaultGoalType(request.getGoalType()));
        goal.setTargetCalories(request.getTargetCalories());
        goal.setTargetProtein(request.getTargetProtein());
        goal.setTargetFat(request.getTargetFat());
        goal.setTargetCarbohydrate(request.getTargetCarbohydrate());
        goal.setTargetWeightKg(request.getTargetWeightKg());
        goal.setWeeklyChangeKg(request.getWeeklyChangeKg());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        goal.setNote(trimToNull(request.getNote()));

        if (goal.getId() == null) {
            healthGoalMapper.insert(goal);
        } else {
            healthGoalMapper.updateById(goal);
        }
        return HealthGoalResponse.from(findGoal(userId));
    }

    public List<WeightLogResponse> listWeightLogs(Long userId, int limit) {
        requireUser(userId);
        return weightLogMapper.selectList(new LambdaQueryWrapper<WeightLog>()
                        .eq(WeightLog::getUserId, userId)
                        .orderByDesc(WeightLog::getRecordDate)
                        .orderByDesc(WeightLog::getId)
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(WeightLogResponse::from)
                .toList();
    }

    @Transactional
    public WeightLogResponse saveWeightLog(Long userId, WeightLogRequest request) {
        requireUser(userId);
        WeightLog existing = weightLogMapper.selectOne(new LambdaQueryWrapper<WeightLog>()
                .eq(WeightLog::getUserId, userId)
                .eq(WeightLog::getRecordDate, request.getRecordDate()));
        if (existing == null) {
            existing = new WeightLog();
            existing.setUserId(userId);
            existing.setRecordDate(request.getRecordDate());
        }
        existing.setWeightKg(request.getWeightKg());
        existing.setNote(trimToNull(request.getNote()));
        if (existing.getId() == null) {
            weightLogMapper.insert(existing);
        } else {
            weightLogMapper.updateById(existing);
        }
        return WeightLogResponse.from(weightLogMapper.selectById(existing.getId()));
    }

    @Transactional
    public void deleteWeightLog(Long userId, Long logId) {
        requireUser(userId);
        WeightLog log = weightLogMapper.selectById(logId);
        if (log == null) {
            throw new IllegalArgumentException("weight log not found");
        }
        if (!userId.equals(log.getUserId())) {
            throw new IllegalArgumentException("cross-user access is not allowed");
        }
        weightLogMapper.deleteById(logId);
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userAccountService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return user;
    }

    private HealthGoal findGoal(Long userId) {
        return healthGoalMapper.selectOne(new LambdaQueryWrapper<HealthGoal>()
                .eq(HealthGoal::getUserId, userId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultGoalType(String goalType) {
        return StringUtils.hasText(goalType) ? goalType.trim() : "BALANCE";
    }
}
