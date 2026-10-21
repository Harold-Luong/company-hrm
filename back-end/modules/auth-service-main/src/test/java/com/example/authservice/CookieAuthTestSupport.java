package com.example.authservice;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

final class CookieAuthTestSupport {
    private CookieAuthTestSupport() {}

    // Other API tests exercise their own concern with a valid CSRF token.
    // BrowserCookieTests covers the real cookie + masked-header exchange.
    static MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(csrf());
    }

    static Cookie refreshCookie(String value) {
        return new Cookie("__Host-hrm-refresh", value);
    }
}
