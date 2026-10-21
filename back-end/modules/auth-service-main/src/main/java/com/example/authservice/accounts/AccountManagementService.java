package com.example.authservice.accounts;

import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.RefreshSessionsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AccountManagementService {
    private final UserRepository users;
    private final RefreshSessionsService sessions;
    private final AccountAuditRepository audits;
    private final AccountLifecycleEvents lifecycle;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    @Value("${hrm.events.enabled:false}") private boolean eventsEnabled;

    @Transactional(readOnly = true)
    public AccountModels.Page<AccountModels.Account> list(String search, AccountStatus status, UserRole role, int page, int size) {
        return AccountModels.Page.of(users.searchAccounts(search.strip(), role, status == null ? null : status.name(),
                PageRequest.of(page, size, Sort.by("id").descending())).map(AccountModels.Account::of));
    }

    @Transactional(readOnly = true)
    public AccountModels.Account get(long id) {
        return AccountModels.Account.of(find(id));
    }

    @Transactional(readOnly = true)
    public AccountModels.Page<AccountAudit> history(long id, int page, int size) {
        find(id);
        return AccountModels.Page.of(audits.findByAccountId(id, PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @Transactional
    public AccountModels.Account execute(long id, AccountModels.Command command, Authentication authentication) {
        long actorId = Long.parseLong(authentication.getName());
        // Self-service logout already exists. Keeping the acting administrator unchanged
        // protects the last active administrator, including two admins modifying each other.
        if (actorId == id) throw conflict("ACCOUNT_SELF_CHANGE_FORBIDDEN");
        // All management requests acquire account locks in the same order. Login/refresh
        // acquire just one account lock, so they cannot form a cycle with this operation.
        User first = lock(Math.min(actorId, id));
        User second = lock(Math.max(actorId, id));
        User actor = actorId < id ? first : second;
        User target = actorId < id ? second : first;
        // Authentication may have happened before waiting for these locks. Re-check the
        // actor's current session and permissions inside the mutation transaction.
        UUID sid = authentication.getDetails() instanceof UUID value ? value : null;
        if (sid == null || !sid.equals(actor.getActiveSessionId()) || !actor.isActive()
                || actor.isActivationPending() || actor.isAdministrativelyLocked())
            throw new GlobalException("Session replaced or signed out", HttpStatus.UNAUTHORIZED);
        if (!actor.getRoles().contains(UserRole.ADMIN))
            throw new GlobalException("Access is denied", HttpStatus.FORBIDDEN);
        if (command.version() != target.getManagementVersion())
            throw new GlobalException("ACCOUNT_VERSION_CONFLICT", HttpStatus.PRECONDITION_FAILED);
        if (command.action() != AccountAction.CHANGE_ROLES && command.roles() != null)
            throw new GlobalException("ACCOUNT_UNEXPECTED_ROLES", HttpStatus.BAD_REQUEST);
        var before = AccountModels.Account.of(target);
        switch (command.action()) {
            case DISABLE -> {
                if (before.status() == AccountStatus.DISABLED) throw conflict("ACCOUNT_ALREADY_DISABLED");
                target.setAdministrativelyLocked(true);
                target.setActive(false);
                // Previously sent invitation links must not reactivate a locked account.
                jdbc.update("UPDATE account_activation_tokens SET revoked_at = ? WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL", Instant.now(), id);
                jdbc.update("""
                        UPDATE activation_mail_outbox SET status = 'CANCELLED', claim_token = NULL, lease_until = NULL
                        WHERE status = 'PENDING' AND id IN (SELECT id FROM account_activation_tokens WHERE user_id = ?)
                        """, id);
            }
            case ENABLE -> {
                if (before.status() != AccountStatus.DISABLED) throw conflict("ACCOUNT_NOT_DISABLED");
                target.setAdministrativelyLocked(false);
                // Unlocking cannot bypass password activation.
                target.setActive(!target.isActivationPending());
            }
            case CHANGE_ROLES -> {
                if (command.roles() == null || command.roles().isEmpty())
                    throw new GlobalException("ACCOUNT_ROLES_REQUIRED", HttpStatus.BAD_REQUEST);
                if (command.roles().equals(target.getRoles())) throw conflict("ACCOUNT_ROLES_UNCHANGED");
                target.setRoles(new HashSet<>(command.roles()));
            }
            case REVOKE_SESSIONS -> { }
        }
        sessions.revokeAllRefreshSessionsByUserId(id);
        target.setManagementVersion(target.getManagementVersion() + 1);
        var after = AccountModels.Account.of(target);
        audits.save(new AccountAudit(AccountModels.Account.of(actor), before, after, command));
        if (eventsEnabled && before.status() != after.status()) lifecycle.publish(target.getEmployeeId(), after.status());
        return after;
    }

    private User lock(long id) {
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new GlobalException("Account not found", HttpStatus.NOT_FOUND));
        // Discard any entity loaded by the authentication filter before the lock was acquired.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    private User find(long id) {
        return users.findById(id).orElseThrow(() -> new GlobalException("Account not found", HttpStatus.NOT_FOUND));
    }
    private GlobalException conflict(String message) { return new GlobalException(message, HttpStatus.CONFLICT); }
}
