package com.example.authservice.accounts;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AccountModels {
    private AccountModels() {}

    public record Command(@NotNull @Min(0) Long version, @NotNull AccountAction action,
            Set<@NotNull UserRole> roles, @NotBlank @Size(max = 500) String reason) {}

    public record Account(Long id, UUID employeeId, String email, AccountStatus status,
            boolean activationPending, Set<UserRole> roles, long version, Instant lastLoginAt,
            Instant createdAt) {
        public static Account of(User user) {
            return new Account(user.getId(), user.getEmployeeId(), user.getEmail(), AccountStatus.of(user),
                    user.isActivationPending(), Set.copyOf(user.getRoles()), user.getManagementVersion(),
                    user.getLastLoginAt(), user.getCreatedAt());
        }
    }
    public record Page<T>(List<T> content, int page, int totalPages, long totalElements) {
        public static <T> Page<T> of(org.springframework.data.domain.Page<T> page) {
            return new Page<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
        }
    }
}
