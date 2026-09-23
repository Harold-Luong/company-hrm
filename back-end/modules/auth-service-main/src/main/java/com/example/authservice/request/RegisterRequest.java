package com.example.authservice.request;

import java.util.Set;
import java.util.UUID;

import com.example.authservice.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 255, message = "Email must not exceed 255 characters") String email,
        @NotBlank(message = "Password is required") String password,
        @NotEmpty Set<UserRole> roles,
        @NotNull(message = "Employee ID is required") UUID employeeId) {
    public RegisterRequest {
        email = email == null ? null : email.trim();
        roles = roles == null
                ? Set.of(UserRole.EMPLOYEE)
                : Set.copyOf(roles);
    }
}
