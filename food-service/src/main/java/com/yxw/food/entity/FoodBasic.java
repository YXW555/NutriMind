package com.yxw.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("food_basics")
public class FoodBasic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String category;

    private Long categoryId;

    private Long conceptId;

    private String barcode;

    private Long ownerUserId;

    private String sourceType;

    private String unit;

    private BigDecimal calories;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carbohydrate;

    private BigDecimal fiber;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
