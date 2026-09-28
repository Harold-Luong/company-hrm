package com.example.authservice;

import com.example.authservice.provisioning.*;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.AuthService;
import com.example.authservice.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"hrm.events.enabled=true", "hrm.events.publisher-enabled=false",
        "hrm.events.listeners-enabled=false", "spring.datasource.url=jdbc:h2:mem:auth-provisioning;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class AccountProvisioningServiceTests {
    @Autowired AccountProvisioningService service;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void prepare() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/004_account_provisioning.sql"));
        }
        for (String table : new String[]{"event_outbox", "account_provisioning_results", "account_link_versions", "refresh_sessions", "user_roles", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void createsOnlyEmployeeRoleAndPendingAccountWithMatchingResult() {
        var event = request("Login@example.com");
        service.process(event);
        var user = users.findByEmail("login@example.com").orElseThrow();
        assertThat(user.getEmployeeId()).isEqualTo(event.data().employeeId());
        assertThat(user.isActive()).isFalse();
        assertThat(user.isActivationPending()).isTrue();
        assertThat(user.getRoles()).containsExactly(com.example.authservice.enums.UserRole.EMPLOYEE);
        assertThatThrownBy(() -> auth.login(new LoginRequest("login@example.com", "anything"), "127.0.0.1"))
                .isInstanceOf(com.example.authservice.exception.GlobalException.class);
        var result = result(event.requestId());
        assertThat(result.eventType()).isEqualTo("AccountCreated");
        assertThat(result.requestId()).isEqualTo(event.requestId());
        assertThat(result.correlationId()).isEqualTo(event.correlationId());
        assertThat(result.data().accountStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(result.data().accountVersion()).isEqualTo(1);
        assertThat(result.data().errorCode()).isNull();
    }

    @Test
    void redeliveryAndSameRequestWithAnotherEventIdDoNotCreateAccountOrResultTwice() {
        var event = request("login@example.com");
        service.process(event);
        service.process(event);
        var redelivery = new EmployeeAccountRequested(UUID.randomUUID(), event.eventType(), 1,
                event.occurredAt(), event.producer(), event.correlationId(), event.requestId(), event.data());
        service.process(redelivery);
        assertThat(users.count()).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(count("account_provisioning_results")).isEqualTo(1);
    }

    @Test
    void duplicateEmailAndEmployeeAreBusinessFailures() {
        var original = request("login@example.com");
        service.process(original);
        var emailConflict = request("LOGIN@example.com");
        service.process(emailConflict);
        assertThat(result(emailConflict.requestId()).data().errorCode()).isEqualTo("EMAIL_ALREADY_USED");
        var employeeConflict = EmployeeAccountRequested.create(UUID.randomUUID(), original.data().employeeId(), "other@example.com", "42");
        service.process(employeeConflict);
        assertThat(result(employeeConflict.requestId()).data().errorCode()).isEqualTo("ACCOUNT_ALREADY_EXISTS");
        assertThat(users.count()).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(3);
    }

    @Test
    void changedContentWithSameRequestIsRejected() {
        var original = request("login@example.com");
        service.process(original);
        var changed = new EmployeeAccountRequested(UUID.randomUUID(), original.eventType(), 1, original.occurredAt(),
                original.producer(), original.correlationId(), original.requestId(),
                new EmployeeAccountRequested.Data(original.data().employeeId(), "changed@example.com", "42"));
        assertThatThrownBy(() -> service.process(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("event_outbox")).isEqualTo(1);
    }

    @Test
    void accountResultAndOutboxRollbackTogether() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.process(request("login@example.com"));
            status.setRollbackOnly();
        });
        assertThat(users.count()).isZero();
        assertThat(count("account_provisioning_results")).isZero();
        assertThat(count("event_outbox")).isZero();
        assertThat(count("account_link_versions")).isZero();
    }

    @Test
    void malformedRequestCreatesNothing() {
        var event = request("invalid");
        assertThatThrownBy(() -> service.process(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.count()).isZero();
        assertThat(count("event_outbox")).isZero();
    }

    @Test
    void concurrentSameEmailCreatesOneAccountAndTwoStableResults() throws Exception {
        var first = request("same@example.com");
        var second = request("same@example.com");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> service.process(first));
            var two = executor.submit(() -> service.process(second));
            one.get(20, java.util.concurrent.TimeUnit.SECONDS);
            two.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(count("account_provisioning_results")).isEqualTo(2);
        assertThat(java.util.List.of(result(first.requestId()).eventType(), result(second.requestId()).eventType()))
                .containsExactlyInAnyOrder("AccountCreated", "AccountCreationFailed");
    }

    private EmployeeAccountRequested request(String email) {
        return EmployeeAccountRequested.create(UUID.randomUUID(), UUID.randomUUID(), email, "42");
    }
    private AccountProvisioningResult result(UUID requestId) {
        return mapper.readValue(jdbc.queryForObject("SELECT result_payload FROM account_provisioning_results WHERE request_id = ?",
                String.class, requestId), AccountProvisioningResult.class);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
