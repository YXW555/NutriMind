package com.yxw.user.dto;

import com.yxw.user.entity.UserProfile;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class HealthProfileResponse {

    private String gender;

    private LocalDate birthDate;

    private BigDecimal heightCm;

    private String activityLevel;

    private String dietaryPreference;

    private String allergies;

    private String medicalNotes;

    public static HealthProfileResponse from(UserProfile profile) {
        if (profile == null) {
            return HealthProfileResponse.builder().build();
        }
        return HealthProfileResponse.builder()
                .gender(profile.getGender())
                .birthDate(profile.getBirthDate())
                .heightCm(profile.getHeightCm())
                .activityLevel(profile.getActivityLevel())
                .dietaryPreference(profile.getDietaryPreference())
                .allergies(profile.getAllergies())
                .medicalNotes(profile.getMedicalNotes())
                .build();
    }
}
