package com.yxw.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodMetadataResponse {

    private Long foodId;

    private Long categoryId;

    private String categoryName;

    private Long conceptId;

    private String conceptCode;

    private String conceptName;

    private String conceptNameEn;

    private List<String> aliases;

    private List<String> conceptAliases;

    private List<FoodImageSampleResponse> imageSamples;
}
