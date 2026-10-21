package com.company.calendar_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Database tests must use disposable PostgreSQL, never the application's datasource. */
@Import(CalendarDatabaseTestSupport.DatabaseConfiguration.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:docs/sql/001_calendar_schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public abstract class CalendarDatabaseTestSupport extends JwtTestSupport {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer calendarPostgres() {
            // Spring owns the lifecycle, including contexts cached across test classes.
            // Service connection details override CALENDAR_DB_* and SPRING_DATASOURCE_*.
            return new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("calendar_test")
                    .withUsername("calendar_test")
                    .withPassword("calendar_test")
                    .withReuse(false);
        }
    }
}
