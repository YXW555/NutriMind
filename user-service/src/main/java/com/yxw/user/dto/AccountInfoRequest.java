package com.yxw.user.dto;

import lombok.Data;

@Data
public class AccountInfoRequest {

    private String nickname;

    private String email;

    private String phone;
}
