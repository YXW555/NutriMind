package com.yxw.user.exception;

import com.yxw.common.core.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "request validation failed";
        return ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException ex) {
        return ApiResponse.fail(HttpStatus.UNAUTHORIZED.value(), "authentication failed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleIllegalStateException(IllegalStateException ex) {
        return ApiResponse.fail(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleDuplicateKeyException(DuplicateKeyException ex) {
        log.warn("Duplicate key exception", ex);
        return ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), resolveDuplicateKeyMessage(ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleDataAccessException(DataAccessException ex) {
        log.error("Database operation failed", ex);
        return ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "database operation failed");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal server error");
    }

    private String resolveDuplicateKeyMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.toLowerCase();
        if (message.contains("uk_user_email") || message.contains(" email ")) {
            return "email already exists";
        }
        if (message.contains("uk_user_phone") || message.contains(" phone ")) {
            return "phone already exists";
        }
        return "username already exists";
    }
}
