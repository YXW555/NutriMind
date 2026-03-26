package com.yxw.user.security;

import com.yxw.user.entity.UserAccount;
import com.yxw.user.service.UserAccountService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountService userAccountService;

    public CustomUserDetailsService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userAccountService.findByIdentifier(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(UserAccount user) {
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + (user.getRole() == null ? "USER" : user.getRole()))
        );

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(user.getStatus() != null && user.getStatus() == 0)
                .credentialsExpired(false)
                .disabled(user.getStatus() != null && user.getStatus() == 0)
                .build();
    }
}
