package com.yxw.meal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdvisorMessageRequest {

    @NotBlank(message = "咨询内容不能为空")
    private String content;
}
