package com.example.authservice.security;

import com.example.authservice.config.AuthCookieProperties;
import com.example.authservice.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookies {
    private final AuthCookieProperties properties;
    private final JwtProperties jwt;

    public String refreshToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, properties.refreshName());
        return cookie == null ? null : cookie.getValue();
    }

    public void setRefresh(HttpServletResponse response, String token) {
        write(response, token, jwt.getRefreshTokenExpiration());
    }

    public void clearRefresh(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    private void write(HttpServletResponse response, String token, Duration lifetime) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(properties.refreshName(), token)
                .httpOnly(true).secure(properties.secure()).sameSite("Strict").path("/")
                .maxAge(lifetime).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
