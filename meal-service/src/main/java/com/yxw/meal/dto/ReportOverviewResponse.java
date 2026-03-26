package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportOverviewResponse {

    private String rangeType;

    private BigDecimal averageCalories;

    private BigDecimal averageProtein;

    private BigDecimal averageFat;

    private BigDecimal averageCarbohydrate;

    private BigDecimal targetCalories;

    private Integer completionRate;

    private Integer recordedDays;

    private String highlightTitle;

    private String highlightDesc;

    private MacroRatioResponse macroRatio;

    private List<ReportTrendPointResponse> trend;
}
