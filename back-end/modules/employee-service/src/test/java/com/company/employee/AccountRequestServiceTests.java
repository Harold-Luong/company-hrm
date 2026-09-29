package com.company.employee;

import com.company.employee.entity.Employee;
import com.company.employee.enums.EmployeeStatus;
import com.company.employee.provisioning.*;
import com.company.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"hrm.events.enabled=true", "hrm.events.publisher-enabled=false",
        "hrm.events.listeners-enabled=false", "spring.datasource.url=jdbc:h2:mem:employee-provisioning;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@WithMockUser(username = "42", roles = "HR")
class AccountRequestServiceTests extends JwtTestSupport {
    @Autowired AccountRequestService service;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired EmployeeRepository employees;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventOutbox outbox;
    private UUID employeeId;

    @BeforeEach
    void prepare() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/001_employee_schema.sql"));
        }
        for (String table : new String[]{"event_outbox", "provisioning_processed_events", "account_provisioning_requests", "employee_account_versions", "employees"}) {
            jdbc.update("DELETE FROM " + table);
        }
        var employee = new Employee();
        employee.setEmployeeCode("EMP-PROVISION"); employee.setEmail("contact@example.com");
        employee.setFirstName("An"); employee.setLastName("Nguyen");
        employee.setHireDate(LocalDate.now()); employee.setStatus(EmployeeStatus.ACTIVE);
        employeeId = employees.saveAndFlush(employee).getId();
    }

    @Test
    void recordsRequestAndOutboxOnceForIdempotentRetries() {
        UUID key = UUID.randomUUID();
        var response = service.request(employeeId, key, new AccountRequestService.Request(" Login@example.com "));
        assertThat(response.provisioningStatus()).isEqualTo("PENDING");
        assertThat(response.accountStatus()).isEqualTo("NOT_CREATED");
        assertThat(service.request(employeeId, key, new AccountRequestService.Request("login@example.com")).requestId()).isEqualTo(response.requestId());
        assertThat(count("event_outbox")).isEqualTo(1);
        var event = requestEvent();
        assertThat(event.data().employeeId()).isEqualTo(employeeId);
        assertThat(event.data().requestedBy()).isEqualTo("42");
        assertThat(event.data().email()).isEqualTo("login@example.com");
        assertThatThrownBy(() -> service.request(employeeId, key, new AccountRequestService.Request("other@example.com")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void deniesNonHrAtServiceBoundary() {
        assertThatThrownBy(() -> service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(count("event_outbox")).isZero();
    }

    @Test
    void missingEmployeeAndInvalidEmailDoNotCreateOutbox() {
        assertThatThrownBy(() -> service.request(UUID.randomUUID(), UUID.randomUUID(), new AccountRequestService.Request("login@example.com")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("invalid")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(count("event_outbox")).isZero();
    }

    @Test
    void rollbackRemovesBothRequestAndEvent() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
            status.setRollbackOnly();
        });
        assertThat(count("account_provisioning_requests")).isZero();
        assertThat(count("event_outbox")).isZero();
        assertThat(employees.existsById(employeeId)).isTrue();
    }

    @Test
    void successIsIdempotentAndProfileEditCannotOverwriteAccountStatus() {
        var response = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        var result = AccountProvisioningResult.of(requestEvent(), null, 1L);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var staleEmployee = employees.findById(employeeId).orElseThrow();
            service.apply(result);
            staleEmployee.setFirstName("Binh");
            employees.saveAndFlush(staleEmployee);
        });
        service.apply(result);
        assertThat(service.find(employeeId, response.requestId()).provisioningStatus()).isEqualTo("SUCCEEDED");
        assertThat(service.find(employeeId, response.requestId()).accountStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(count("provisioning_processed_events")).isEqualTo(1);
    }

    @Test
    void oldAccountVersionDoesNotOverwriteNewerStatusButCompletesMatchingRequest() {
        var response = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        jdbc.update("INSERT INTO employee_account_versions (employee_id, version) VALUES (?, 2)", employeeId);
        jdbc.update("UPDATE employees SET account_status = 'DISABLED' WHERE id = ?", employeeId);
        service.apply(AccountProvisioningResult.of(requestEvent(), null, 1L));
        var current = service.find(employeeId, response.requestId());
        assertThat(current.provisioningStatus()).isEqualTo("SUCCEEDED");
        assertThat(current.accountStatus()).isEqualTo("DISABLED");
    }

    @Test
    void failedRequestCanBeRetriedAndLateDuplicateCannotCompleteNewRequest() {
        var first = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        var failure = AccountProvisioningResult.of(requestEvent(), "EMAIL_ALREADY_USED", null);
        service.apply(failure);
        assertThat(service.find(employeeId, first.requestId()).provisioningStatus()).isEqualTo("FAILED");
        var next = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("new@example.com"));
        service.apply(failure);
        assertThat(service.find(employeeId, next.requestId()).provisioningStatus()).isEqualTo("PENDING");
        assertThat(service.find(employeeId, next.requestId()).accountStatus()).isEqualTo("NOT_CREATED");
    }

    @Test
    void mismatchedCorrelationIsRejectedWithoutChangingRequest() {
        var response = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        var event = AccountProvisioningResult.of(requestEvent(), null, 1L);
        var mismatched = new AccountProvisioningResult(event.eventId(), event.eventType(), 1, Instant.now(),
                "auth-service", UUID.randomUUID(), event.requestId(), event.data());
        assertThatThrownBy(() -> service.apply(mismatched)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.find(employeeId, response.requestId()).provisioningStatus()).isEqualTo("PENDING");
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldWorkerCannotAcknowledgeIt() {
        service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        var first = outbox.claim();
        assertThat(outbox.claim()).isNull();
        jdbc.update("UPDATE event_outbox SET lease_until = ?", java.time.OffsetDateTime.now().minusMinutes(3));
        var second = outbox.claim();
        outbox.sent(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE sent_at IS NOT NULL", Integer.class)).isZero();
        outbox.sent(second);
        assertThat(outbox.claim()).isNull();
    }

    @Test
    void activationBeforeProvisioningResultKeepsActiveStatusAndDuplicateLifecycleIsHarmless() {
        var response = service.request(employeeId, UUID.randomUUID(), new AccountRequestService.Request("login@example.com"));
        var active = new AccountLifecycleService.Event(UUID.randomUUID(), "AccountStatusChanged", 1, Instant.now(),
                "auth-service", new AccountLifecycleService.Data(employeeId, "ACTIVE", 2L));
        lifecycle.apply(active);
        lifecycle.apply(active);
        service.apply(AccountProvisioningResult.of(requestEvent(), null, 1L));
        assertThat(service.find(employeeId, response.requestId()).accountStatus()).isEqualTo("ACTIVE");
        assertThat(service.find(employeeId, response.requestId()).provisioningStatus()).isEqualTo("SUCCEEDED");
        lifecycle.apply(new AccountLifecycleService.Event(UUID.randomUUID(), "AccountStatusChanged", 1, Instant.now(),
                "auth-service", new AccountLifecycleService.Data(employeeId, "PENDING_ACTIVATION", 1L)));
        assertThat(service.find(employeeId, response.requestId()).accountStatus()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> lifecycle.apply(new AccountLifecycleService.Event(UUID.randomUUID(), "AccountStatusChanged", 1, Instant.now(),
                "other-service", new AccountLifecycleService.Data(employeeId, "DISABLED", 3L)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.find(employeeId, response.requestId()).accountStatus()).isEqualTo("ACTIVE");
    }

    private EmployeeAccountRequested requestEvent() {
        return mapper.readValue(jdbc.queryForObject("SELECT payload FROM event_outbox", String.class), EmployeeAccountRequested.class);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
