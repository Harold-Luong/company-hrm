package com.example.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

// Hibernate validates the actual complete SQL instead of generating a substitute schema.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:docs/sql/001_auth_schema.sql"
})
class AuthSchemaTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;

    @Test
    void completeSchemaHasAllEightTablesAndCanBeReplayedWithoutLosingAccounts() throws Exception {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (employee_id, email, password_hash) VALUES (?, ?, ?)", id, id + "@example.com", "hash");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/001_auth_schema.sql"));
        }
        for (String table : new String[]{"users", "user_roles", "refresh_sessions", "account_provisioning_results",
                "account_link_versions", "event_outbox", "account_activation_tokens", "activation_mail_outbox"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE employee_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void schemaEnforcesActivationAndRoleConstraints() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (employee_id, email, password_hash, is_active, activation_pending) VALUES (?, ?, 'hash', TRUE, TRUE)",
                UUID.randomUUID(), "pending@example.com")).isInstanceOf(DataIntegrityViolationException.class);
        var employeeId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (employee_id, email, password_hash) VALUES (?, ?, 'hash')", employeeId, employeeId + "@example.com");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE employee_id = ?", Long.class, employeeId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'SUPERUSER')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'ADMIN')", userId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'ADMIN')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void documentedSeedPasswordMatchesTheActualBcryptHash() throws Exception {
        String seed = Files.readString(Path.of("docs/sql/002_auth_seed.sql"));
        var match = Pattern.compile("\\$2a\\$12\\$[./A-Za-z0-9]{53}").matcher(seed);
        assertThat(match.find()).isTrue();
        assertThat(passwords.matches("Admin@123456", match.group())).isTrue();
        assertThat(passwords.matches("password123", match.group())).isFalse();
    }
}
