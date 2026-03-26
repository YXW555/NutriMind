package com.yxw.meal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorMessageResponse {

    private Long id;

    private String role;

    private String content;

    private List<AdvisorReferenceResponse> references;

    private LocalDateTime createdAt;
}
