package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("meal_plan")
public class MealPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate planDate;

    private String title;

    private String notes;

    private String status;

    private BigDecimal totalCalories;

    private BigDecimal totalProtein;

    private BigDecimal totalFat;

    private BigDecimal totalCarbohydrate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
