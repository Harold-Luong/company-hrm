package com.company.employee;

import com.company.employee.entity.*;
import com.company.employee.enums.ProvisioningStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// Validate the real SQL against Hibernate mappings instead of generating substitute tables.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:employee-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:docs/sql/001_employee_schema.sql"
})
@ActiveProfiles("test")
class EmployeeSchemaTests extends JwtTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;

    private UUID insertEmployee() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO employees (id, employee_code, first_name, last_name, personal_email, gender, hire_date) "
                + "VALUES (?, ?, 'An', 'Nguyen', ?, 'OTHER', CURRENT_DATE)", id, id.toString(), id + "@example.com");
        return id;
    }

    @Test
    void completeSchemaCanBeReplayedWithoutLosingProfilesOrAccountState() throws Exception {
        var id = insertEmployee();
        Long allocated = jdbc.queryForObject("SELECT nextval('employee_code_seq')", Long.class);
        jdbc.update("UPDATE employees SET employee_account_status = 'ACTIVE' WHERE id = ?", id);
        jdbc.update("INSERT INTO employee_account_versions (employee_id, version) VALUES (?, 7)", id);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/001_employee_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/003_employee_code_sequence.sql"));
        }
        assertThat(jdbc.queryForObject("SELECT nextval('employee_code_seq')", Long.class)).isEqualTo(allocated + 1);
        for (String table : new String[]{"departments", "positions", "employees", "account_provisioning_requests",
                "provisioning_processed_events", "employee_account_versions", "event_outbox"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT employee_account_status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM employee_account_versions WHERE employee_id = ?", Long.class, id))
                .isEqualTo(7L);
    }

    @Test
    void newProfilesDefaultToNotCreatedAndRejectInvalidStatuses() {
        var id = insertEmployee();
        assertThat(jdbc.queryForObject("SELECT employee_account_status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("NOT_CREATED");
        assertThat(jdbc.queryForObject("SELECT status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("PROBATION");
        for (String invalid : new String[]{"UNKNOWN", "FAILED", "PENDING", null}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET employee_account_status = ? WHERE id = ?", invalid, id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE employees SET status = 'UNKNOWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void infrastructureEntitiesRoundTripAgainstSqlSchema() {
        UUID employeeId = insertEmployee();
        UUID requestId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var request = AccountProvisioningRequest.builder()
                .requestId(requestId).employee(entityManager.find(Employee.class, employeeId))
                .pendingEmployeeId(employeeId).email("login@example.com").requestedBy("42")
                .idempotencyKey(UUID.randomUUID()).correlationId(UUID.randomUUID())
                .status(ProvisioningStatus.PENDING).build();
        entityManager.persist(request);
        entityManager.persist(ProvisioningProcessedEvent.builder().eventId(eventId).requestId(requestId).build());
        entityManager.persist(EmployeeAccountVersion.builder().employeeId(employeeId).version(2L).build());
        entityManager.persist(OutboxEvent.builder().eventId(eventId).topic("employee-events")
                .messageKey(employeeId.toString()).payload("{\"test\":true}").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(AccountProvisioningRequest.class, requestId).getStatus()).isEqualTo(ProvisioningStatus.PENDING);
        assertThat(entityManager.find(ProvisioningProcessedEvent.class, eventId).getProcessedAt()).isNotNull();
        assertThat(entityManager.find(EmployeeAccountVersion.class, employeeId).getVersion()).isEqualTo(2L);
        var outbox = entityManager.find(OutboxEvent.class, eventId);
        assertThat(outbox.getPayload()).isEqualTo("{\"test\":true}");
        assertThat(outbox.getCreatedAt()).isNotNull();
        assertThat(outbox.getNextAttemptAt()).isNotNull();
        assertThat(outbox.getAttempts()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM account_provisioning_requests WHERE request_id = ?",
                String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    void schemaAcceptsCurrentEnumsAndRejectsMissingOrUnknownGender() {
        UUID id = insertEmployee();
        for (var status : com.company.employee.enums.EmployeeStatus.values()) {
            jdbc.update("UPDATE employees SET status = ? WHERE id = ?", status.name(), id);
        }
        for (var status : com.company.employee.enums.EmployeeAccountStatus.values()) {
            jdbc.update("UPDATE employees SET employee_account_status = ? WHERE id = ?", status.name(), id);
        }
        for (var gender : com.company.employee.enums.Gender.values()) {
            jdbc.update("UPDATE employees SET gender = ? WHERE id = ?", gender.name(), id);
        }
        for (String invalid : new String[]{"UNKNOWN", null}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET gender = ? WHERE id = ?", invalid, id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void schemaRejectsMissingRelationsAndSelfManagement() {
        var id = insertEmployee();
        assertThatThrownBy(() -> jdbc.update("UPDATE employees SET manager_id = id WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String column : new String[]{"department_id", "position_id", "manager_id"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
