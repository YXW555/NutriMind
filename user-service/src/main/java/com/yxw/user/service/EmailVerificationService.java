package com.yxw.user.service;

public interface EmailVerificationService {

    void sendRegisterCode(String email);

    void verifyRegisterCode(String email, String verifyCode);

    boolean isEnabled();
}
