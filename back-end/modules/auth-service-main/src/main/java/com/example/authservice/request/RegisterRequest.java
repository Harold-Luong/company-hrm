package com.example.authservice.request;

import com.example.authservice.exception.ExceptionMessages;
import java.util.Set;
import java.util.UUID;

import com.example.authservice.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = ExceptionMessages.EMAIL_REQUIRED) @Email(message = ExceptionMessages.INVALID_EMAIL) @Size(max = 255, message = ExceptionMessages.EMAIL_TOO_LONG) String email,
        @NotBlank(message = ExceptionMessages.PASSWORD_REQUIRED) String password,
        @NotEmpty Set<UserRole> roles,
        @NotNull(message = ExceptionMessages.EMPLOYEE_ID_REQUIRED) UUID employeeId) {
    public RegisterRequest {
        email = email == null ? null : email.trim();
        roles = roles == null
                ? Set.of(UserRole.EMPLOYEE)
                : Set.copyOf(roles);
    }
}
