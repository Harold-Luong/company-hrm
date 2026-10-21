package com.example.authservice.accounts;

import com.example.authservice.enums.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/accounts")
@RequiredArgsConstructor
public class AccountManagementController {
    private final AccountManagementService service;

    @GetMapping
    public AccountModels.Page<AccountModels.Account> list(
            @RequestParam(defaultValue = "") @Size(max = 255) String search,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(search, status, role, page, size);
    }

    @GetMapping("/{id}")
    public AccountModels.Account get(@PathVariable @Positive long id) { return service.get(id); }

    @GetMapping("/{id}/audit")
    public AccountModels.Page<AccountAudit> history(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(id, page, size);
    }

    // Bearer-only authentication: cookies alone never authorize administration.
    @PostMapping("/{id}/actions")
    public AccountModels.Account execute(@PathVariable @Positive long id,
            @Valid @RequestBody AccountModels.Command command, Authentication authentication) {
        return service.execute(id, command, authentication);
    }
}
