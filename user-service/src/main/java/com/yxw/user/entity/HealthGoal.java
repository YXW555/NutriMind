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
@TableName("health_goal")
public class HealthGoal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String goalType;

    private BigDecimal targetCalories;

    private BigDecimal targetProtein;

    private BigDecimal targetFat;

    private BigDecimal targetCarbohydrate;

    private BigDecimal targetWeightKg;

    private BigDecimal weeklyChangeKg;

    private LocalDate startDate;

    private LocalDate endDate;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
