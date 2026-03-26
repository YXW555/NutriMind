package com.yxw.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthAvailabilityResponse {

    private boolean available;

    private String normalizedUsername;

    private String message;
}
