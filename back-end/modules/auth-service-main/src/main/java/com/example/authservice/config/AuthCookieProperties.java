package com.example.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.cookies")
public record AuthCookieProperties(Boolean secure) {
    public AuthCookieProperties {
        if (secure == null)
            secure = true;
    }

    public String refreshName() {
        return secure ? "__Host-hrm-refresh" : "hrm-refresh-dev";
    }

    public String csrfName() {
        return secure ? "__Host-hrm-csrf" : "hrm-csrf-dev";
    }
}
