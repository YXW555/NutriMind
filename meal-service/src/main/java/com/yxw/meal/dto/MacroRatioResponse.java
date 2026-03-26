package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroRatioResponse {

    private Integer proteinPercent;

    private Integer carbohydratePercent;

    private Integer fatPercent;

    private BigDecimal protein;

    private BigDecimal carbohydrate;

    private BigDecimal fat;
}
