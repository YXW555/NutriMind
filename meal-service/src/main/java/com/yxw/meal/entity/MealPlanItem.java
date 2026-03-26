package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("meal_plan_item")
public class MealPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long foodId;

    private String mealType;

    private BigDecimal quantity;

    private String note;

    private Integer sortOrder;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
