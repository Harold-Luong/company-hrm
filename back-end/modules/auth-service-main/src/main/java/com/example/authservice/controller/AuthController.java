package com.example.authservice.controller;

import com.example.authservice.exception.ExceptionMessages;
import com.example.authservice.request.LoginRequest;
import com.example.authservice.request.RefreshTokenRequest;
import com.example.authservice.request.RegisterRequest;
import com.example.authservice.response.LoginResponse;
import com.example.authservice.security.AuthCookies;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletResponse;
import com.example.authservice.response.UserResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.HealthCheckService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final HealthCheckService healthCheckService;
    private final AuthCookies cookies;

    public record BrowserTokens(String tokenType, String accessToken, Long accessTokenExpiresIn) {
    }

    public record BrowserLogin(String message, BrowserTokens data) {
    }

    public record BrowserRefresh(String accessToken) {
    }

    public record CsrfResponse(String token, String headerName, boolean sessionPresent) {
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(new CsrfResponse(token.getToken(), token.getHeaderName(), cookies.refreshToken(request) != null));
    }

    @GetMapping("/health-check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        boolean connected = healthCheckService.isDatabaseConnected();
        return ResponseEntity.status(connected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", connected ? "UP" : "DOWN",
                        "database", connected ? "UP" : "DOWN"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok().body(Map.of("message", "User registered successfully!"));
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserLogin> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        LoginResponse result = authService.login(request, httpRequest.getRemoteAddr());
        cookies.setRefresh(response, result.data().refreshToken());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new BrowserLogin(result.message(),
                new BrowserTokens(result.data().tokenType(), result.data().accessToken(),
                        result.data().accessTokenExpiresIn())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BrowserRefresh> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookies.refreshToken(request);
        if (token == null || token.isBlank())
            throw new com.example.authservice.exception.GlobalException(ExceptionMessages.REFRESH_COOKIE_REQUIRED,
                    HttpStatus.UNAUTHORIZED);
        var result = authService.refresh(token);
        cookies.setRefresh(response, result.refreshToken());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new BrowserRefresh(result.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String token = cookies.refreshToken(request);
            if (token != null && !token.isBlank())
                authService.logout(new RefreshTokenRequest(token));
        } finally {
            cookies.clearRefresh(response);
        }
        return ResponseEntity.ok().body(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication, HttpServletResponse response) {
        authService.logoutAll(authentication);
        cookies.clearRefresh(response);
        return ResponseEntity.ok().body(Map.of("message", "All sessions logged out successfully"));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session() {
        // JwtAuthenticationFilter has verified the signature and current session in DB.
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
