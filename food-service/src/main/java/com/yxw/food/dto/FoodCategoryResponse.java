package com.yxw.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodCategoryResponse {

    private Long id;

    private String name;

    private Long parentId;

    private String description;

    private Integer sortOrder;

    private Integer status;
}
