package com.company.employee;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatusMigrationTests {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migratesOldRowsAndPreservesReconciledStatusesOnRerun(boolean hasLegacyColumn) throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:account-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "")) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE TABLE employees (id INTEGER PRIMARY KEY)");
            jdbc.update("INSERT INTO employees (id) VALUES (1), (2)");
            if (hasLegacyColumn) {
                jdbc.execute("ALTER TABLE employees ADD COLUMN has_account BOOLEAN NOT NULL DEFAULT FALSE");
                jdbc.update("UPDATE employees SET has_account = TRUE WHERE id = 1");
            }
            var migration = new ClassPathResource("static/sql/003_replace_has_account_with_account_status.sql");
            ScriptUtils.executeSqlScript(connection, migration);

            assertThat(jdbc.queryForList("SELECT account_status FROM employees ORDER BY id", String.class))
                    .containsExactly("UNKNOWN", "UNKNOWN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = 'EMPLOYEES' AND COLUMN_NAME = 'HAS_ACCOUNT'", Integer.class))
                    .isZero();
            jdbc.update("INSERT INTO employees (id) VALUES (3)");
            assertThat(jdbc.queryForObject("SELECT account_status FROM employees WHERE id = 3", String.class))
                    .isEqualTo("NOT_CREATED");

            jdbc.update("UPDATE employees SET account_status = 'DISABLED' WHERE id = 1");
            ScriptUtils.executeSqlScript(connection, migration);
            assertThat(jdbc.queryForObject("SELECT account_status FROM employees WHERE id = 1", String.class))
                    .isEqualTo("DISABLED");
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET account_status = 'FAILED' WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE employees SET account_status = NULL WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
