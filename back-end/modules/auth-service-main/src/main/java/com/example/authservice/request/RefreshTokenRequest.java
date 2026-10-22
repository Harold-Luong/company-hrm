package com.example.authservice.request;

import com.example.authservice.exception.ExceptionMessages;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = ExceptionMessages.REFRESH_TOKEN_REQUIRED) String refreshToken) {
}
