package com.yxw.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerificationCodeSendRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email format is invalid")
    private String email;
}
