package com.company.leave.config;

import com.company.leave.exception.ExceptionMessages;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import com.company.leave.security.AccessTokenClaimsValidator;
import com.company.leave.security.SessionVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.Assert;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SessionVerifier sessionVerifier(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl) {
        return new SessionVerifier(authServiceUrl);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) throws IOException {
        try (var input = properties.accessPublicKey().getInputStream()) {
            var publicKey = RsaKeyConverters.x509().convert(input);
            Assert.notNull(publicKey, ExceptionMessages.JWT_PUBLIC_KEY_REQUIRED);
            Assert.isTrue(publicKey.getModulus().bitLength() >= 2048,
                    ExceptionMessages.JWT_PUBLIC_KEY_TOO_SHORT);
            var decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(Duration.ZERO),
                    new JwtIssuerValidator(properties.accessIssuer()),
                    new JwtClaimValidator<List<String>>("aud",
                            audiences -> audiences != null && audiences.contains(properties.accessAudience())),
                    new AccessTokenClaimsValidator()));
            return decoder;
        }
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter converter, SessionVerifier sessions, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            if (exception instanceof OAuth2AuthenticationException oauth
                    && "temporarily_unavailable".equals(oauth.getError().getErrorCode())) {
                writeProblem(mapper, response, 503, ExceptionMessages.SERVICE_UNAVAILABLE_TITLE, ExceptionMessages.SESSION_VERIFICATION_UNAVAILABLE);
                return;
            }
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeProblem(mapper, response, 401, ExceptionMessages.UNAUTHORIZED_TITLE, ExceptionMessages.ACCESS_TOKEN_REQUIRED);
        };
        AccessDeniedHandler forbidden = (request, response, exception) -> writeProblem(mapper, response, 403, ExceptionMessages.FORBIDDEN_TITLE,
                ExceptionMessages.ACCESS_DENIED);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/leave/requests/inbox",
                                "/api/v1/leave/requests/adjustments/inbox",
                                "/api/v1/leave/requests/pending-count").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/leave/requests/{id}/approve",
                                "/api/v1/leave/requests/{id}/adjustments/{adjustmentId}/approve",
                                "/api/v1/leave/requests/{id}/adjustments/{adjustmentId}/reject",
                                "/api/v1/leave/requests/{id}/reject").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/leave/requests").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/leave/requests/{id}/adjustments").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/leave/requests/{id}/adjustments").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/leave/requests/{id}/adjustments/{adjustmentId}/cancel").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/leave/requests/mine",
                                "/api/v1/leave/requests/attendance",
                                "/api/v1/leave/requests/calendar",
                                "/api/v1/leave/requests/balance",
                                "/api/v1/leave/requests/{id}", "/api/v1/leave/requests/{id}/history").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/leave/requests/{id}/cancel").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            sessions.verify(token.getTokenValue());
                            return converter.convert(token);
                        })));
        return http.build();
    }

    private static void writeProblem(ObjectMapper mapper, HttpServletResponse response, int status,
            String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(Map.of(
                "type", "about:blank", "title", title, "status", status, "detail", detail)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173"));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"));

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("ETag", "Location"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
