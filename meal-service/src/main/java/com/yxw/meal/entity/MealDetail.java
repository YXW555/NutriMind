package com.yxw.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("meal_detail")
public class MealDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long foodId;

    private String mealType;

    private BigDecimal quantity;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
