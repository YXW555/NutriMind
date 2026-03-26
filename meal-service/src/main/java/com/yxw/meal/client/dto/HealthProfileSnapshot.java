package com.yxw.meal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthProfileSnapshot {

    private String gender;

    private LocalDate birthDate;

    private BigDecimal heightCm;

    private String activityLevel;

    private String dietaryPreference;

    private String allergies;

    private String medicalNotes;
}
