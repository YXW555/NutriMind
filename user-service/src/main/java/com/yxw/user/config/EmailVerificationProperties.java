package com.yxw.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.verification.email")
public class EmailVerificationProperties {

    private boolean enabled = false;

    private String from;

    private String subject = "知食分子注册验证码";

    private long expireSeconds = 300;

    private long cooldownSeconds = 60;
}
