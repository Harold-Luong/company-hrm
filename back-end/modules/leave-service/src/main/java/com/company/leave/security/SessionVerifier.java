package com.company.leave.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** Check the current login with Auth after local JWT verification. Never cache acceptance. */
public final class SessionVerifier {
    private final URI endpoint;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();

    public SessionVerifier(String authServiceUrl) {
        endpoint = URI.create(authServiceUrl.replaceAll("/+$", "") + "/api/v1/auth/session");
    }

    public void verify(String accessToken) {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + accessToken).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 204) return;
            if (status == 401 || status == 403) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Session replaced or signed out");
            }
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException e) {
            throw unavailable();
        }
    }

    private OAuth2AuthenticationException unavailable() {
        return new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"),
                "Session verification is unavailable");
    }
}
