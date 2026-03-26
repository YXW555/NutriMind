package com.yxw.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String gender;

    private LocalDate birthDate;

    private BigDecimal heightCm;

    private String activityLevel;

    private String dietaryPreference;

    private String allergies;

    private String medicalNotes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
