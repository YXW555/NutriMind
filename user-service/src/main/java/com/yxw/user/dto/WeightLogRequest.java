package com.yxw.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WeightLogRequest {

    @NotNull(message = "weightKg must not be null")
    @DecimalMin(value = "20.00", message = "weight must be at least 20kg")
    @DecimalMax(value = "400.00", message = "weight must be at most 400kg")
    private BigDecimal weightKg;

    @NotNull(message = "recordDate must not be null")
    private LocalDate recordDate;

    private String note;
}
