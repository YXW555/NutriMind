package com.yxw.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yxw.user.entity.UserAccount;

public interface UserAccountService extends IService<UserAccount> {

    UserAccount findByUsername(String username);

    UserAccount findByEmail(String email);

    UserAccount findByPhone(String phone);

    UserAccount findByIdentifier(String identifier);
}
