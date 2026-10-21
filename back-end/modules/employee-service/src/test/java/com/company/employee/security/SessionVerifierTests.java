package com.company.employee.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.assertj.core.api.Assertions.*;

class SessionVerifierTests {
    private HttpServer auth;
    private SessionVerifier verifier;
    private final AtomicInteger status = new AtomicInteger(204);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> bearer = new AtomicReference<>();

    @BeforeEach
    void startAuth() throws Exception {
        auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/api/v1/auth/session", exchange -> {
            calls.incrementAndGet();
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Location", "/must-not-follow");
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        auth.start();
        verifier = new SessionVerifier("http://127.0.0.1:" + auth.getAddress().getPort() + "/");
    }

    @AfterEach void stopAuth() { auth.stop(0); }

    @Test
    void forwardsBearerAndDoesNotCacheAPreviouslyAcceptedSession() {
        verifier.verify("test-access");
        assertThat(bearer.get()).isEqualTo("Bearer test-access");
        status.set(401);
        assertError("invalid_token");
        assertThat(calls.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 200, 302, 500, 503})
    void onlyAnExplicitNoContentResponseAcceptsTheSession(int code) {
        status.set(code);
        assertError(code == 401 || code == 403 ? "invalid_token" : "temporarily_unavailable");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void connectionFailureDoesNotAcceptTheSessionOrReportInvalidCredentials() {
        auth.stop(0);
        assertError("temporarily_unavailable");
    }

    private void assertError(String code) {
        assertThatThrownBy(() -> verifier.verify("test-access"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo(code));
    }
}
