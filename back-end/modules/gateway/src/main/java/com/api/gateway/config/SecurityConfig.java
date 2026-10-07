package com.api.gateway.config;

import com.api.gateway.security.AccessTokenClaimsValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({ JwtProperties.class, CorsProperties.class })
public class SecurityConfig {
    @Bean
    ReactiveJwtDecoder jwtDecoder(JwtProperties properties) throws IOException {
        try (var input = properties.accessPublicKey().getInputStream()) {
            var key = RsaKeyConverters.x509().convert(input);
            Assert.notNull(key, "JWT access public key is required");
            Assert.isTrue(key.getModulus().bitLength() >= 2048, "RSA key must be at least 2048 bits");
            var decoder = NimbusReactiveJwtDecoder.withPublicKey(key)
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(Duration.ZERO),
                    new JwtIssuerValidator(properties.accessIssuer()),
                    new JwtClaimValidator<List<String>>("aud",
                            values -> values != null && values.contains(properties.accessAudience())),
                    new AccessTokenClaimsValidator()));
            return decoder;
        }
    }

    // Auth validates refresh tokens in the body; an expired access token in
    // Authorization must not prevent obtaining a new one.
    @Bean
    @Order(0)
    SecurityWebFilterChain refreshSecurity(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return stateless(http, cors)
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh"))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain apiSecurity(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return stateless(http, cors)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/activate",
                                "/activation/index.html", "/activation/activate.js", "/activation/style.css")
                        .permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout",
                                "/api/v1/auth/activate")
                        .permitAll()
                        .pathMatchers("/api/v1/auth/**", "/api/v1/employees/**", "/api/v1/departments/**",
                                "/api/v1/positions/**", "/api/v1/calendar/**", "/api/v1/calendar-events/**",
                                "/api/v1/leave/**", "/api/v1/attendance/**")
                        .authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> {
                        })
                        .authenticationEntryPoint((exchange, exception) -> problem(exchange, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, exception) -> problem(exchange, HttpStatus.FORBIDDEN)))
                .build();
    }

    private ServerHttpSecurity stateless(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(spec -> spec.configurationSource(cors))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, exception) -> problem(exchange, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, exception) -> problem(exchange, HttpStatus.FORBIDDEN)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept",
                "If-Match", "If-None-Match", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("ETag", "Location", "Retry-After", "WWW-Authenticate"));
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        var body = ("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
