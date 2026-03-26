package com.yxw.user.dto;

import com.yxw.user.entity.WeightLog;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class WeightLogResponse {

    private Long id;

    private BigDecimal weightKg;

    private LocalDate recordDate;

    private String note;

    private LocalDateTime createdAt;

    public static WeightLogResponse from(WeightLog log) {
        if (log == null) {
            return null;
        }
        return WeightLogResponse.builder()
                .id(log.getId())
                .weightKg(log.getWeightKg())
                .recordDate(log.getRecordDate())
                .note(log.getNote())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
