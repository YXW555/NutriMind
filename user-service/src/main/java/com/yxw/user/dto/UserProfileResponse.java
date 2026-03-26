package com.yxw.user.dto;

import com.yxw.user.entity.UserAccount;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String role;

    public static UserProfileResponse from(UserAccount user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }
}
