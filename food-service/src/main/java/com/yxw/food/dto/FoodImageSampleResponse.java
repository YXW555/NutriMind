package com.yxw.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodImageSampleResponse {

    private Long id;

    private String imageUrl;

    private String source;

    private String description;

    private Integer sortOrder;
}
