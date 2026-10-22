package com.example.authservice.request;

import com.example.authservice.exception.ExceptionMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = ExceptionMessages.EMAIL_REQUIRED)
        @Email(message = ExceptionMessages.INVALID_EMAIL)
        @Size(max = 255, message = ExceptionMessages.EMAIL_TOO_LONG)
        String email,
        @NotBlank(message = ExceptionMessages.PASSWORD_REQUIRED)
        String password) {
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
