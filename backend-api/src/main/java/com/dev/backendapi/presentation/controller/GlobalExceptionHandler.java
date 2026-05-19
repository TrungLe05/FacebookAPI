package com.dev.backendapi.presentation.controller;

import com.dev.backendapi.infrastructure.exception.FacebookApiException;
import com.dev.backendapi.presentation.dto.response.ApiResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
        return ApiResponse.builder()
                .code(400)
                .message(msg)
                .build();
    }

    @ExceptionHandler(FacebookApiException.class)
    public ApiResponse<?> handleFacebook(FacebookApiException ex) {
        return ApiResponse.builder()
                .code(502)
                .message("Facebook API error: " + ex.getMessage())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleGeneral(Exception ex) {
        return ApiResponse.builder()
                .code(500)
                .message(ex.getMessage())
                .build();
    }
}
