package com.example.authservice.config;

import com.example.authservice.security.jwt.JwtAuthenticationFilter;

import lombok.AllArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ JwtProperties.class, AuthCookieProperties.class })
@AllArgsConstructor
public class SecurityConfig {

    public final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(AuthCookieProperties cookies) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName(cookies.csrfName());
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(cookies.secure()).sameSite("Strict"));
        return repository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokens)
            throws Exception {

        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(new OrRequestMatcher(
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"),
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout-all"))))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"401\",\"message\":\"Authentication is required\"}");
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"403\",\"message\":\"Access is denied\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/health-check", "/api/v1/auth/csrf", "/activate",
                                "/activation/index.html")
                        .permitAll()
                        .requestMatchers("/openapi.yaml", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/activate").permitAll()
                        .requestMatchers("/api/v1/auth/activation-invitations/**").hasAnyRole("ADMIN", "HR")
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/auth/logout-all").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
