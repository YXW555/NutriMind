package com.yxw.user.service.impl;

import com.yxw.user.config.EmailVerificationProperties;
import com.yxw.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REGISTER_CODE_PREFIX = "verify:register:email:";
    private static final String REGISTER_COOLDOWN_PREFIX = "verify:cooldown:register:email:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    @Override
    public void sendRegisterCode(String email) {
        ensureEnabled();
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("email format is invalid");
        }

        String cooldownKey = buildCooldownKey(normalizedEmail);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            throw new IllegalArgumentException("verification code requested too frequently");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(
                buildCodeKey(normalizedEmail),
                code,
                Duration.ofSeconds(properties.getExpireSeconds())
        );
        stringRedisTemplate.opsForValue().set(
                cooldownKey,
                "1",
                Duration.ofSeconds(properties.getCooldownSeconds())
        );

        try {
            sendEmail(normalizedEmail, code);
        } catch (Exception ex) {
            stringRedisTemplate.delete(buildCodeKey(normalizedEmail));
            stringRedisTemplate.delete(cooldownKey);
            log.error("Failed to send verification email to {}", normalizedEmail, ex);
            throw new IllegalStateException("failed to send verification email");
        }
    }

    @Override
    public void verifyRegisterCode(String email, String verifyCode) {
        ensureEnabled();
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("email format is invalid");
        }
        if (!StringUtils.hasText(verifyCode)) {
            throw new IllegalArgumentException("verification code must not be blank");
        }

        String codeKey = buildCodeKey(normalizedEmail);
        String expectedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(expectedCode)) {
            throw new IllegalArgumentException("verification code expired or not found");
        }
        if (!expectedCode.equals(verifyCode.trim())) {
            throw new IllegalArgumentException("verification code is invalid");
        }

        stringRedisTemplate.delete(codeKey);
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("email verification is not enabled");
        }
        if (!StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException("verification email sender is not configured");
        }
    }

    private void sendEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(email);
        message.setSubject(properties.getSubject());
        message.setText(buildMailContent(code));
        mailSender.send(message);
    }

    private String buildMailContent(String code) {
        long expireMinutes = Math.max(1, TimeUnit.SECONDS.toMinutes(properties.getExpireSeconds()));
        return """
                您好，欢迎使用知食分子。
                您本次注册验证码为：%s

                验证码将在 %d 分钟内有效，请勿泄露给他人。
                如非本人操作，请忽略本邮件。
                """.formatted(code, expireMinutes);
    }

    private String buildCodeKey(String email) {
        return REGISTER_CODE_PREFIX + email;
    }

    private String buildCooldownKey(String email) {
        return REGISTER_COOLDOWN_PREFIX + email;
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private String generateCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
