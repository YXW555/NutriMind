package com.yxw.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.user.entity.UserAccount;
import com.yxw.user.mapper.UserAccountMapper;
import com.yxw.user.service.UserAccountService;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Override
    public UserAccount findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return lambdaQuery().eq(UserAccount::getUsername, username.trim()).one();
    }

    @Override
    public UserAccount findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return lambdaQuery().eq(UserAccount::getEmail, email.trim()).list().stream().findFirst().orElse(null);
    }

    @Override
    public UserAccount findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return lambdaQuery().eq(UserAccount::getPhone, phone.trim()).list().stream().findFirst().orElse(null);
    }

    @Override
    public UserAccount findByIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String normalized = identifier.trim();
        return lambdaQuery()
                .eq(UserAccount::getUsername, normalized)
                .or()
                .eq(UserAccount::getEmail, normalized)
                .or()
                .eq(UserAccount::getPhone, normalized)
                .list()
                .stream()
                .findFirst()
                .orElse(null);
    }
}
