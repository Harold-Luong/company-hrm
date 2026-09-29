package com.company.employee;

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

    private UUID insertEmployee() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO employees (id, employee_code, first_name, last_name, email, hire_date) "
                + "VALUES (?, ?, 'An', 'Nguyen', ?, CURRENT_DATE)", id, id.toString(), id + "@example.com");
        return id;
    }

    @Test
    void completeSchemaCanBeReplayedWithoutLosingProfilesOrAccountState() throws Exception {
        var id = insertEmployee();
        jdbc.update("UPDATE employees SET account_status = 'ACTIVE' WHERE id = ?", id);
        jdbc.update("INSERT INTO employee_account_versions (employee_id, version) VALUES (?, 7)", id);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/001_employee_schema.sql"));
        }
        for (String table : new String[]{"departments", "positions", "employees", "account_provisioning_requests",
                "provisioning_processed_events", "employee_account_versions", "event_outbox"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT account_status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM employee_account_versions WHERE employee_id = ?", Long.class, id))
                .isEqualTo(7L);
    }

    @Test
    void newProfilesDefaultToNotCreatedAndRejectInvalidStatuses() {
        var id = insertEmployee();
        assertThat(jdbc.queryForObject("SELECT account_status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("NOT_CREATED");
        assertThat(jdbc.queryForObject("SELECT status FROM employees WHERE id = ?", String.class, id))
                .isEqualTo("PROBATION");
        for (String invalid : new String[]{"UNKNOWN", "FAILED", "PENDING", null}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET account_status = ? WHERE id = ?", invalid, id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE employees SET status = 'UNKNOWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
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
