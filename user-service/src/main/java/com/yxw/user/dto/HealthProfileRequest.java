package com.yxw.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HealthProfileRequest {

    private String gender;

    private LocalDate birthDate;

    @DecimalMin(value = "50.00", message = "height must be at least 50cm")
    @DecimalMax(value = "260.00", message = "height must be at most 260cm")
    private BigDecimal heightCm;

    private String activityLevel;

    private String dietaryPreference;

    private String allergies;

    private String medicalNotes;
}
