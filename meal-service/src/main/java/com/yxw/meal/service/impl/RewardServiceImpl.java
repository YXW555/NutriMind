package com.yxw.meal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxw.meal.dto.RewardBadgeResponse;
import com.yxw.meal.dto.RewardFeedbackResponse;
import com.yxw.meal.dto.RewardSummaryResponse;
import com.yxw.meal.entity.MealDetail;
import com.yxw.meal.entity.RewardAccount;
import com.yxw.meal.entity.RewardLog;
import com.yxw.meal.entity.UserBadge;
import com.yxw.meal.mapper.RewardAccountMapper;
import com.yxw.meal.mapper.RewardLogMapper;
import com.yxw.meal.mapper.UserBadgeMapper;
import com.yxw.meal.service.RewardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RewardServiceImpl implements RewardService {

    private static final int MEAL_TYPE_POINTS = 2;
    private static final int COMPLETE_DAY_POINTS = 8;
    private static final Set<String> CORE_MEAL_TYPES = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private static final List<BadgeDefinition> BADGE_DEFINITIONS = List.of(
            new BadgeDefinition("STREAK_3", "饮食起步勋章", "连续记录 3 天饮食", 3),
            new BadgeDefinition("STREAK_7", "自律新星勋章", "连续记录 7 天饮食", 7),
            new BadgeDefinition("STREAK_14", "健康坚持者勋章", "连续记录 14 天饮食", 14)
    );

    private final RewardAccountMapper rewardAccountMapper;
    private final RewardLogMapper rewardLogMapper;
    private final UserBadgeMapper userBadgeMapper;

    public RewardServiceImpl(RewardAccountMapper rewardAccountMapper,
                             RewardLogMapper rewardLogMapper,
                             UserBadgeMapper userBadgeMapper) {
        this.rewardAccountMapper = rewardAccountMapper;
        this.rewardLogMapper = rewardLogMapper;
        this.userBadgeMapper = userBadgeMapper;
    }

    @Override
    @Transactional
    public RewardFeedbackResponse handleMealRecorded(Long userId,
                                                     LocalDate recordDate,
                                                     Set<String> beforeMealTypes,
                                                     List<MealDetail> currentDetails) {
        RewardAccount account = getOrCreateAccount(userId);
        Set<String> normalizedBefore = normalizeMealTypes(beforeMealTypes);
        Set<String> normalizedCurrent = normalizeMealTypes(
                currentDetails.stream().map(MealDetail::getMealType).collect(Collectors.toCollection(LinkedHashSet::new))
        );

        int pointsEarned = 0;
        List<String> messages = new ArrayList<>();

        Set<String> newMealTypes = new LinkedHashSet<>(normalizedCurrent);
        newMealTypes.removeAll(normalizedBefore);
        for (String mealType : newMealTypes) {
            if (awardPointsIfAbsent(account, userId, "MEAL_TYPE", buildMealTypeBizKey(userId, recordDate, mealType),
                    MEAL_TYPE_POINTS, buildMealTypeTitle(mealType), "完成一次餐次记录", recordDate)) {
                pointsEarned += MEAL_TYPE_POINTS;
                messages.add(buildMealTypeTitle(mealType) + " +" + MEAL_TYPE_POINTS + "积分");
            }
        }

        if (normalizedCurrent.containsAll(CORE_MEAL_TYPES)
                && !normalizedBefore.containsAll(CORE_MEAL_TYPES)
                && awardPointsIfAbsent(account, userId, "DAILY_COMPLETE", buildDailyCompleteBizKey(userId, recordDate),
                COMPLETE_DAY_POINTS, "三餐打卡完成", "早餐、午餐、晚餐记录齐全", recordDate)) {
            pointsEarned += COMPLETE_DAY_POINTS;
            messages.add("三餐打卡完成 +" + COMPLETE_DAY_POINTS + "积分");
        }

        if (shouldUpdateStreak(account.getLastCheckInDate(), recordDate)) {
            int streak = calculateNextStreak(account.getLastCheckInDate(), account.getCurrentStreak(), recordDate);
            account.setCurrentStreak(streak);
            account.setLastCheckInDate(recordDate);
            rewardAccountMapper.updateById(account);
            for (BadgeDefinition definition : BADGE_DEFINITIONS) {
                if (streak >= definition.threshold && awardBadgeIfAbsent(account, userId, definition)) {
                    messages.add("解锁勋章：" + definition.badgeName);
                }
            }
        }

        RewardAccount latest = rewardAccountMapper.selectById(userId);
        return RewardFeedbackResponse.builder()
                .pointsEarned(pointsEarned)
                .totalPoints(latest == null ? 0 : safeInt(latest.getTotalPoints()))
                .badgeCount(latest == null ? 0 : safeInt(latest.getBadgeCount()))
                .currentStreak(latest == null ? 0 : safeInt(latest.getCurrentStreak()))
                .messages(messages)
                .build();
    }

    @Override
    public RewardSummaryResponse getSummary(Long userId) {
        RewardAccount account = getOrCreateAccount(userId);
        List<RewardBadgeResponse> badges = userBadgeMapper.selectList(new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
                        .orderByDesc(UserBadge::getEarnedAt)
                        .last("LIMIT 3"))
                .stream()
                .map(RewardBadgeResponse::from)
                .toList();

        return RewardSummaryResponse.builder()
                .userId(userId)
                .totalPoints(safeInt(account.getTotalPoints()))
                .badgeCount(safeInt(account.getBadgeCount()))
                .currentStreak(safeInt(account.getCurrentStreak()))
                .lastCheckInDate(account.getLastCheckInDate())
                .badges(badges)
                .build();
    }

    private RewardAccount getOrCreateAccount(Long userId) {
        RewardAccount account = rewardAccountMapper.selectById(userId);
        if (account != null) {
            return account;
        }
        RewardAccount created = new RewardAccount();
        created.setUserId(userId);
        created.setTotalPoints(0);
        created.setBadgeCount(0);
        created.setCurrentStreak(0);
        rewardAccountMapper.insert(created);
        return rewardAccountMapper.selectById(userId);
    }

    private boolean awardPointsIfAbsent(RewardAccount account,
                                        Long userId,
                                        String eventType,
                                        String bizKey,
                                        int points,
                                        String title,
                                        String description,
                                        LocalDate recordDate) {
        RewardLog existing = rewardLogMapper.selectOne(new LambdaQueryWrapper<RewardLog>()
                .eq(RewardLog::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        RewardLog log = new RewardLog();
        log.setUserId(userId);
        log.setEventType(eventType);
        log.setBizKey(bizKey);
        log.setPoints(points);
        log.setTitle(title);
        log.setDescription(description);
        log.setRecordDate(recordDate);
        rewardLogMapper.insert(log);

        account.setTotalPoints(safeInt(account.getTotalPoints()) + points);
        rewardAccountMapper.updateById(account);
        return true;
    }

    private boolean awardBadgeIfAbsent(RewardAccount account, Long userId, BadgeDefinition definition) {
        UserBadge existing = userBadgeMapper.selectOne(new LambdaQueryWrapper<UserBadge>()
                .eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getBadgeCode, definition.badgeCode)
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        UserBadge badge = new UserBadge();
        badge.setUserId(userId);
        badge.setBadgeCode(definition.badgeCode);
        badge.setBadgeName(definition.badgeName);
        badge.setBadgeDescription(definition.badgeDescription);
        userBadgeMapper.insert(badge);

        account.setBadgeCount(safeInt(account.getBadgeCount()) + 1);
        rewardAccountMapper.updateById(account);
        return true;
    }

    private Set<String> normalizeMealTypes(Set<String> mealTypes) {
        if (mealTypes == null || mealTypes.isEmpty()) {
            return Set.of();
        }
        return mealTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(type -> type.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean shouldUpdateStreak(LocalDate lastCheckInDate, LocalDate recordDate) {
        return recordDate != null && (lastCheckInDate == null || recordDate.isAfter(lastCheckInDate));
    }

    private int calculateNextStreak(LocalDate lastCheckInDate, Integer currentStreak, LocalDate recordDate) {
        if (lastCheckInDate == null) {
            return 1;
        }
        if (recordDate.equals(lastCheckInDate.plusDays(1))) {
            return safeInt(currentStreak) + 1;
        }
        return 1;
    }

    private String buildMealTypeBizKey(Long userId, LocalDate recordDate, String mealType) {
        return "MEAL_TYPE:" + userId + ":" + recordDate + ":" + mealType;
    }

    private String buildDailyCompleteBizKey(Long userId, LocalDate recordDate) {
        return "DAILY_COMPLETE:" + userId + ":" + recordDate;
    }

    private String buildMealTypeTitle(String mealType) {
        return switch (mealType) {
            case "BREAKFAST" -> "早餐打卡";
            case "LUNCH" -> "午餐打卡";
            case "DINNER" -> "晚餐打卡";
            default -> "加餐打卡";
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record BadgeDefinition(String badgeCode, String badgeName, String badgeDescription, int threshold) {
    }
}
