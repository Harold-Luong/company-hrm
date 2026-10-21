package com.api.gateway.security;

import java.time.Duration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class SessionVerifier {
    private final WebClient client;

    public SessionVerifier(String authServiceUrl) {
        client = WebClient.builder().baseUrl(authServiceUrl.replaceAll("/+$", "")).build();
    }

    public Mono<Void> verify(String accessToken) {
        return client.get().uri("/api/v1/auth/session")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (status == 204) return response.releaseBody();
                    var error = status == 401 || status == 403
                            ? new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Session replaced or signed out")
                            : unavailable();
                    return response.releaseBody().then(Mono.<Void>error(error));
                })
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(error -> !(error instanceof OAuth2AuthenticationException), error -> unavailable());
    }

    private OAuth2AuthenticationException unavailable() {
        return new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"),
                "Session verification is unavailable");
    }
}
