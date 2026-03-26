package com.yxw.user.controller;

import com.yxw.common.core.ApiResponse;
import com.yxw.common.core.security.JwtTokenService;
import com.yxw.common.core.security.SecurityContextUtils;
import com.yxw.user.dto.AuthAvailabilityResponse;
import com.yxw.user.dto.AuthRequest;
import com.yxw.user.dto.AuthResponse;
import com.yxw.user.dto.UserProfileResponse;
import com.yxw.user.entity.UserAccount;
import com.yxw.user.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\-]{7,20}$");

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserAccountService userAccountService,
                          PasswordEncoder passwordEncoder,
                          JwtTokenService jwtTokenService,
                          AuthenticationManager authenticationManager) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/check-username")
    public ApiResponse<AuthAvailabilityResponse> checkUsername(@RequestParam String username) {
        String normalizedUsername = normalizeUsername(username);
        String validationMessage = validateUsernameMessage(normalizedUsername);
        if (validationMessage != null) {
            return ApiResponse.success(new AuthAvailabilityResponse(false, normalizedUsername, validationMessage));
        }

        boolean available = userAccountService.findByUsername(normalizedUsername) == null;
        return ApiResponse.success(new AuthAvailabilityResponse(
                available,
                normalizedUsername,
                available ? "username is available" : "username already exists"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = normalizePassword(request.getPassword());
        String confirmPassword = normalizePassword(request.getConfirmPassword());
        String nickname = normalizeOptional(request.getNickname());
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());

        validateRegisterRequest(username, password, confirmPassword, nickname, email, phone);

        if (userAccountService.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(400, "username already exists"));
        }
        if (StringUtils.hasText(email) && userAccountService.findByEmail(email) != null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(400, "email already exists"));
        }
        if (StringUtils.hasText(phone) && userAccountService.findByPhone(phone) != null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(400, "phone already exists"));
        }

        UserAccount user = UserAccount.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(StringUtils.hasText(nickname) ? nickname : username)
                .email(email)
                .phone(phone)
                .role("USER")
                .status(1)
                .build();
        boolean saved = userAccountService.save(user);
        if (!saved) {
            throw new RuntimeException("register failed");
        }

        if (user.getId() == null) {
            user = userAccountService.findByUsername(username);
        }
        if (user == null || user.getId() == null) {
            throw new RuntimeException("register failed");
        }

        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
        AuthResponse response = new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname());
        return ResponseEntity.ok(ApiResponse.success("register success", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        try {
            String identifier = normalizeIdentifier(request.getUsername());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, normalizePassword(request.getPassword()))
            );
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            UserAccount user = userAccountService.findByUsername(userDetails.getUsername());
            if (user == null) {
                throw new BadCredentialsException("user not found");
            }
            user.setLastLoginAt(LocalDateTime.now());
            userAccountService.updateById(user);

            String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
            AuthResponse response = new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname());
            return ResponseEntity.ok(ApiResponse.success("login success", response));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(ApiResponse.fail(401, "invalid username or password"));
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> currentUser() {
        Long currentUserId = SecurityContextUtils.requireCurrentUserId();
        UserAccount user = userAccountService.getById(currentUserId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return ApiResponse.success(UserProfileResponse.from(user));
    }

    private void validateRegisterRequest(String username,
                                         String password,
                                         String confirmPassword,
                                         String nickname,
                                         String email,
                                         String phone) {
        String usernameMessage = validateUsernameMessage(username);
        if (usernameMessage != null) {
            throw new IllegalArgumentException(usernameMessage);
        }
        if (!StringUtils.hasText(password) || password.length() < 6 || password.length() > 32) {
            throw new IllegalArgumentException("password must be 6-32 characters");
        }
        if (!Objects.equals(password, confirmPassword)) {
            throw new IllegalArgumentException("confirm password does not match");
        }
        if (StringUtils.hasText(nickname) && nickname.length() > 20) {
            throw new IllegalArgumentException("nickname must be at most 20 characters");
        }
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email format is invalid");
        }
        if (StringUtils.hasText(phone) && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("phone format is invalid");
        }
    }

    private String validateUsernameMessage(String username) {
        if (!StringUtils.hasText(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            return "username must be 4-20 characters and contain only letters, numbers, or underscores";
        }
        return null;
    }

    private String normalizeUsername(String username) {
        return normalizeOptional(username);
    }

    private String normalizeIdentifier(String identifier) {
        String normalized = normalizeOptional(identifier);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (normalized.contains("@")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeOptional(email);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        String normalized = normalizeOptional(phone);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replace(" ", "");
    }

    private String normalizePassword(String password) {
        return password == null ? "" : password.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
