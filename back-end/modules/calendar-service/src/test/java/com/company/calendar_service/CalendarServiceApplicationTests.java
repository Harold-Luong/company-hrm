package com.company.calendar_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/calendar_db",
        "spring.datasource.username=application_user_must_not_be_used",
        "spring.datasource.password=application_password_must_not_be_used"
})
class CalendarServiceApplicationTests extends CalendarDatabaseTestSupport {
    @Autowired DataSource dataSource;
    @Autowired PostgreSQLContainer postgres;

    @Test
    void usesDisposableDatabaseEvenWhenApplicationDatasourceIsConfigured() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database(), current_user")) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("calendar_test");
            assertThat(result.getString(2)).isEqualTo("calendar_test");
        }
    }
}
