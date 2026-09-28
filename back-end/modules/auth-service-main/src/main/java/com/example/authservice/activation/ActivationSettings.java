package com.example.authservice.activation;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;

@Component
public class ActivationSettings {
    @Value("${auth.activation.enabled:false}") public boolean enabled;
    @Value("${auth.activation.token-secret:}") private String secret;
    @Value("${auth.activation.token-ttl:24h}") public Duration ttl;
    @Value("${auth.activation.resend-cooldown:60s}") public Duration cooldown;
    @Value("${auth.activation.frontend-url:http://localhost:8080/activate}") public String frontendUrl;
    @Value("${auth.activation.mail-from:}") public String sender;
    @Value("${auth.activation.resend-api-key:}") public String apiKey;

    public byte[] secretBytes() { return Base64.getDecoder().decode(secret); }

    @PostConstruct
    void validate() {
        if (!enabled) return;
        if (secretBytes().length < 32 || apiKey.isBlank() || sender.isBlank())
            throw new IllegalStateException("Activation requires a base64 secret of at least 32 bytes, RESEND_API_KEY and RESEND_FROM");
        var url = URI.create(frontendUrl);
        boolean local = "http".equals(url.getScheme()) && java.util.Set.of("localhost", "127.0.0.1").contains(url.getHost());
        if ((!"https".equals(url.getScheme()) && !local) || url.getHost() == null
                || url.getRawQuery() != null || url.getRawFragment() != null || url.getUserInfo() != null)
            throw new IllegalStateException("Activation frontend URL must use HTTPS (HTTP allowed only for localhost) without query or fragment");
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofDays(7)) > 0
                || cooldown.compareTo(Duration.ofSeconds(30)) < 0)
            throw new IllegalStateException("Activation TTL must be positive and at most 7 days; resend cooldown must be at least 30 seconds");
    }
}
