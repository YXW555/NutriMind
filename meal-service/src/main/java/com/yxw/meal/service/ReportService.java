package com.yxw.meal.service;

import com.yxw.meal.dto.MacroRatioResponse;
import com.yxw.meal.dto.ReportOverviewResponse;
import com.yxw.meal.dto.ReportTrendPointResponse;
import com.yxw.meal.entity.MealRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final BigDecimal TARGET_CALORIES = BigDecimal.valueOf(2000);
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    private final MealRecordService mealRecordService;

    public ReportService(MealRecordService mealRecordService) {
        this.mealRecordService = mealRecordService;
    }

    public ReportOverviewResponse getOverview(Long userId, String rangeType) {
        String normalizedRange = "month".equalsIgnoreCase(rangeType) ? "month" : "week";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = "month".equals(normalizedRange) ? endDate.minusDays(29) : endDate.minusDays(6);

        List<MealRecord> records = mealRecordService.lambdaQuery()
                .eq(MealRecord::getUserId, userId)
                .between(MealRecord::getRecordDate, startDate, endDate)
                .orderByAsc(MealRecord::getRecordDate)
                .list();

        Map<LocalDate, MealRecord> recordMap = records.stream()
                .collect(Collectors.toMap(MealRecord::getRecordDate, Function.identity(), (left, right) -> right));

        List<ReportTrendPointResponse> trend = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            MealRecord record = recordMap.get(cursor);
            BigDecimal calories = record == null ? BigDecimal.ZERO : zeroSafe(record.getTotalCalories());
            BigDecimal protein = record == null ? BigDecimal.ZERO : zeroSafe(record.getTotalProtein());
            BigDecimal fat = record == null ? BigDecimal.ZERO : zeroSafe(record.getTotalFat());
            BigDecimal carbohydrate = record == null ? BigDecimal.ZERO : zeroSafe(record.getTotalCarbohydrate());
            trend.add(ReportTrendPointResponse.builder()
                    .label(buildLabel(cursor, normalizedRange))
                    .date(cursor)
                    .calories(calories)
                    .protein(protein)
                    .fat(fat)
                    .carbohydrate(carbohydrate)
                    .completionRate(toPercent(calories, TARGET_CALORIES))
                    .build());
            cursor = cursor.plusDays(1);
        }

        int totalDays = trend.size();
        int recordedDays = (int) trend.stream()
                .filter(item -> item.getCalories().compareTo(BigDecimal.ZERO) > 0)
                .count();

        BigDecimal totalCalories = sum(records.stream().map(MealRecord::getTotalCalories).toList());
        BigDecimal totalProtein = sum(records.stream().map(MealRecord::getTotalProtein).toList());
        BigDecimal totalFat = sum(records.stream().map(MealRecord::getTotalFat).toList());
        BigDecimal totalCarbohydrate = sum(records.stream().map(MealRecord::getTotalCarbohydrate).toList());

        BigDecimal averageCalories = divide(totalCalories, totalDays);
        BigDecimal averageProtein = divide(totalProtein, totalDays);
        BigDecimal averageFat = divide(totalFat, totalDays);
        BigDecimal averageCarbohydrate = divide(totalCarbohydrate, totalDays);

        ReportTrendPointResponse peakPoint = trend.stream()
                .max((left, right) -> left.getCalories().compareTo(right.getCalories()))
                .orElse(null);

        return ReportOverviewResponse.builder()
                .rangeType(normalizedRange)
                .averageCalories(averageCalories)
                .averageProtein(averageProtein)
                .averageFat(averageFat)
                .averageCarbohydrate(averageCarbohydrate)
                .targetCalories(TARGET_CALORIES)
                .completionRate(toPercent(averageCalories, TARGET_CALORIES))
                .recordedDays(recordedDays)
                .highlightTitle(buildHighlightTitle(averageCalories, recordedDays, totalDays))
                .highlightDesc(buildHighlightDesc(normalizedRange, averageProtein, peakPoint))
                .macroRatio(buildMacroRatio(totalProtein, totalCarbohydrate, totalFat))
                .trend(trend)
                .build();
    }

    private String buildLabel(LocalDate date, String rangeType) {
        if ("month".equals(rangeType)) {
            return date.format(MONTH_LABEL_FORMATTER);
        }
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String buildHighlightTitle(BigDecimal averageCalories, int recordedDays, int totalDays) {
        if (recordedDays == 0) {
            return "这段时间还没有记录";
        }
        if (recordedDays < totalDays / 2) {
            return "记录频率还可以再稳一点";
        }
        if (averageCalories.compareTo(BigDecimal.valueOf(1400)) < 0) {
            return "整体摄入偏低";
        }
        if (averageCalories.compareTo(BigDecimal.valueOf(2200)) > 0) {
            return "这段时间热量偏高";
        }
        return "最近状态保持得不错";
    }

    private String buildHighlightDesc(String rangeType, BigDecimal averageProtein, ReportTrendPointResponse peakPoint) {
        String timeScope = "month".equals(rangeType) ? "本月" : "本周";
        String peakText = peakPoint == null
                ? "暂时还没有明显高峰日。"
                : String.format(Locale.ROOT, "%s 热量最高的是 %s（%s 千卡）。", timeScope, peakPoint.getLabel(), format(peakPoint.getCalories()));
        if (averageProtein.compareTo(BigDecimal.valueOf(60)) < 0) {
            return peakText + " 蛋白质均值还偏低，建议多安排鸡蛋、牛奶、豆腐和鸡胸肉。";
        }
        return peakText + " 蛋白质均值维持得不错，继续保持规律记录就能更快看出趋势。";
    }

    private MacroRatioResponse buildMacroRatio(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat) {
        BigDecimal proteinCalories = protein.multiply(BigDecimal.valueOf(4));
        BigDecimal carbohydrateCalories = carbohydrate.multiply(BigDecimal.valueOf(4));
        BigDecimal fatCalories = fat.multiply(BigDecimal.valueOf(9));
        BigDecimal total = proteinCalories.add(carbohydrateCalories).add(fatCalories);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return MacroRatioResponse.builder()
                    .proteinPercent(0)
                    .carbohydratePercent(0)
                    .fatPercent(0)
                    .protein(BigDecimal.ZERO)
                    .carbohydrate(BigDecimal.ZERO)
                    .fat(BigDecimal.ZERO)
                    .build();
        }
        return MacroRatioResponse.builder()
                .proteinPercent(toPercent(proteinCalories, total))
                .carbohydratePercent(toPercent(carbohydrateCalories, total))
                .fatPercent(toPercent(fatCalories, total))
                .protein(protein.setScale(2, RoundingMode.HALF_UP))
                .carbohydrate(carbohydrate.setScale(2, RoundingMode.HALF_UP))
                .fat(fat.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private int toPercent(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal divide(BigDecimal value, int divisor) {
        if (divisor <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .map(this::zeroSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String format(BigDecimal value) {
        return zeroSafe(value).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
