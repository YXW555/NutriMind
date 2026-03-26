package com.yxw.meal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeightLogSnapshot {

    private Long id;

    private BigDecimal weightKg;

    private LocalDate recordDate;

    private String note;

    private LocalDateTime createdAt;
}
