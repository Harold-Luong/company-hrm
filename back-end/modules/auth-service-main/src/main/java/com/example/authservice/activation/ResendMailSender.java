package com.example.authservice.activation;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ResendMailSender {
    private final ActivationSettings settings;
    private final ActivationTokens tokens;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI endpoint;

    @org.springframework.beans.factory.annotation.Autowired
    public ResendMailSender(ActivationSettings settings, ActivationTokens tokens, ObjectMapper mapper) {
        this(settings, tokens, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://api.resend.com/emails"));
    }

    // Package-private transport seam for tests; production destination cannot be
    // overridden by a request.
    ResendMailSender(ActivationSettings settings, ActivationTokens tokens, ObjectMapper mapper, HttpClient client,
            URI endpoint) {
        this.settings = settings;
        this.tokens = tokens;
        this.mapper = mapper;
        this.client = client;
        this.endpoint = endpoint;
    }

    public record Delivery(String id, String error, boolean retryable, long retryAfter) {
    }

    public Delivery send(ActivationMailQueue.Mail mail) {
        String link = mail.frontendUrl() + "?token=" + tokens.derive(mail.id());
        // Version 1 template must remain stable for in-flight retries using the same
        // idempotency key.
        String body = mapper.writeValueAsString(Map.of("from", mail.sender(), "to", List.of(mail.recipient()),
                "subject", "Kích hoạt tài khoản Company HRM",
                "text", "Bạn được mời sử dụng Company HRM. Mở liên kết để đặt mật khẩu và kích hoạt tài khoản:\n\n"
                        + link + "\n\nLiên kết hết hạn lúc " + mail.expiresAt().toInstant()
                        + ". Nếu liên kết hết hạn, vui lòng liên hệ HR để gửi lại lời mời. Không chia sẻ liên kết này."));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + settings.apiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "hrm-activation-v1/" + mail.id())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                String id = mapper.readTree(response.body()).path("id").asText();
                if (id.isBlank() || id.length() > 255)
                    return new Delivery(null, "INVALID_PROVIDER_RESPONSE", true, 0);
                return new Delivery(id, null, false, 0);
            }
            if (status == 409
                    && "invalid_idempotent_request".equals(mapper.readTree(response.body()).path("name").asText()))
                return new Delivery(null, "RESEND_IDEMPOTENCY_CONFLICT", false, 0);
            // Persist only an HTTP code; provider response bodies can contain
            // credentials/recipient data.
            long retryAfter = 0;
            try {
                retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"));
            } catch (NumberFormatException ignored) {
                /* use bounded exponential backoff */ }
            return new Delivery(null, "RESEND_HTTP_" + status,
                    status == 408 || status == 409 || status == 429 || status >= 500, retryAfter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Delivery(null, "DELIVERY_INTERRUPTED", true, 0);
        } catch (Exception e) {
            return new Delivery(null, "DELIVERY_UNAVAILABLE", true, 0);
        }
    }
}
