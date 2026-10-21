package com.example.authservice.accounts;

import com.example.authservice.entity.User;

public enum AccountStatus {
    ACTIVE, DISABLED, PENDING_ACTIVATION;

    public static AccountStatus of(User user) {
        if (user.isAdministrativelyLocked()) return DISABLED;
        if (user.isActivationPending()) return PENDING_ACTIVATION;
        return user.isActive() ? ACTIVE : DISABLED;
    }
}
