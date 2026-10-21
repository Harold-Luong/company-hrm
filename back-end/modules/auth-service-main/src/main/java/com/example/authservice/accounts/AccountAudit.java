package com.example.authservice.accounts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.stream.Collectors;

@Entity
@Table(name = "account_management_audit")
@Getter
@NoArgsConstructor
public class AccountAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false) private String actorEmail;
    @Column(nullable = false) private Long accountId;
    @Column(nullable = false) private String accountEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AccountAction action;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AccountStatus beforeStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AccountStatus afterStatus;
    @Column(nullable = false, length = 100) private String beforeRoles;
    @Column(nullable = false, length = 100) private String afterRoles;
    @Column(nullable = false) private long version;
    @Column(nullable = false) private Instant createdAt;

    public AccountAudit(AccountModels.Account actor, AccountModels.Account before,
            AccountModels.Account after, AccountModels.Command command) {
        actorId = actor.id(); actorEmail = actor.email();
        accountId = before.id(); accountEmail = before.email();
        action = command.action(); reason = command.reason().strip();
        beforeStatus = before.status(); afterStatus = after.status();
        beforeRoles = roles(before); afterRoles = roles(after);
        version = after.version(); createdAt = Instant.now();
    }

    private static String roles(AccountModels.Account account) {
        return account.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
