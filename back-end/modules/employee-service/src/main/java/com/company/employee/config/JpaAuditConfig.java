package com.company.employee.config;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditDateTimeProvider")
public class JpaAuditConfig {

    @Bean
    public DateTimeProvider auditDateTimeProvider() {
        // Match PostgreSQL timestamp precision so create/read responses agree.
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext()
                    .getAuthentication();

            if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
                return Optional.empty();
            }

            return Optional.of(
                    UUID.fromString(jwtAuth.getToken().getClaimAsString("employee_id")));
        };
    }
}
