package com.api.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {
    private static final KeyPair KEYS = keys();
    private static final AtomicInteger UPSTREAM_CALLS = new AtomicInteger();
    private static final HttpServer AUTH = backend("auth");
    private static final HttpServer EMPLOYEE = backend("employee");
    private static final HttpServer WORKFORCE = backend("workforce");
    private static final Path PUBLIC_KEY = publicKey();

    @LocalServerPort
    private int port;
    private WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.access-public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("AUTH_SERVICE_URL", () -> url(AUTH));
        registry.add("EMPLOYEE_SERVICE_URL", () -> url(EMPLOYEE));
        registry.add("WORKFORCE_SERVICE_URL", () -> url(WORKFORCE));
        registry.add("gateway.cors.allowed-origins", () -> "https://hrm.example.com");
    }

    @BeforeEach
    void client() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @AfterAll
    static void stopBackends() throws Exception {
        for (var backend : List.of(AUTH, EMPLOYEE, WORKFORCE)) {
            backend.stop(0);
        }
        Files.deleteIfExists(PUBLIC_KEY);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/auth/me,auth",
            "/api/v1/employees,employee",
            "/api/v1/employees/42/account-requests,employee",
            "/api/v1/departments,employee",
            "/api/v1/positions/42,employee",
            "/api/v1/calendar,workforce",
            "/api/v1/calendar-events/42,workforce"
    })
    void routesOriginalPathQueryAndBearerToken(String path, String backend) throws Exception {
        String token = sign(claims(), KEYS, JWSAlgorithm.RS256);
        client.get().uri(path + "?year=2026").headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Test-Backend", backend)
                .expectHeader().valueEquals("X-Test-Authorization", "Bearer " + token)
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody(String.class).isEqualTo(path + "?year=2026|");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/activate"})
    void anonymousAuthPostsPreserveBody(String path) {
        String body = "{\"refreshToken\":\"test-token\"}";
        client.post().uri(path).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Test-Backend", "auth")
                .expectBody(String.class).isEqualTo(path + "|" + body);
    }

    @Test
    void refreshAcceptsExpiredAccessTokenWhileAuthValidatesRefreshBody() throws Exception {
        String expired = sign(claims().expirationTime(Date.from(Instant.now().minusSeconds(60))), KEYS, JWSAlgorithm.RS256);
        client.post().uri("/api/v1/auth/refresh").headers(headers -> headers.setBearerAuth(expired))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"refreshToken\":\"test\"}")
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Test-Backend", "auth");
        client.get().uri("/api/v1/auth/me").headers(headers -> headers.setBearerAuth(expired))
                .exchange().expectStatus().isUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/logout-all", "/api/v1/auth/activation-invitations/42",
            "/api/v1/employees", "/api/v1/departments", "/api/v1/positions", "/api/v1/calendar", "/api/v1/calendar-events"})
    void protectedApisNeverReachBackendWithoutToken(String path) {
        int calls = UPSTREAM_CALLS.get();
        client.post().uri(path).exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "no-expiry", "future", "subject", "employee", "roles", "refresh", "signature", "algorithm"})
    void rejectsInvalidTokensBeforeRouting(String invalid) throws Exception {
        var claims = claims();
        switch (invalid) {
            case "issuer" -> claims.issuer("untrusted");
            case "audience" -> claims.audience("hrm-api-refresh");
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(1)));
            case "no-expiry" -> claims.expirationTime(null);
            case "future" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)));
            case "subject" -> claims.subject(null);
            case "employee" -> claims.claim("employee_id", "invalid");
            case "roles" -> claims.claim("roles", List.of());
            case "refresh" -> claims.claim("type", "refresh");
        }
        String token = sign(claims, invalid.equals("signature") ? keys() : KEYS,
                invalid.equals("algorithm") ? JWSAlgorithm.RS512 : JWSAlgorithm.RS256);
        int calls = UPSTREAM_CALLS.get();
        client.get().uri("/api/v1/employees").headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isUnauthorized();
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(calls);
    }

    @Test
    void corsPreflightAndErrorsAreHandledAtGateway() {
        int calls = UPSTREAM_CALLS.get();
        client.options().uri("/api/v1/calendar-events").header(HttpHeaders.ORIGIN, "https://hrm.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,if-match,idempotency-key")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://hrm.example.com");
        client.get().uri("/api/v1/employees").header(HttpHeaders.ORIGIN, "https://hrm.example.com")
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://hrm.example.com");
        client.options().uri("/api/v1/employees").header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(calls);
    }

    @Test
    void removesSpoofedIdentityAndForwardingHeadersAndTerminatesCors() throws Exception {
        String token = sign(claims(), KEYS, JWSAlgorithm.RS256);
        client.get().uri("/api/v1/calendar").headers(headers -> headers.setBearerAuth(token))
                .header("Forwarded", "for=203.0.113.99;proto=https;host=evil.example")
                .header("X-Forwarded-For", "203.0.113.99")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-User-Id", "admin").header("X-Employee-Id", "admin").header("X-Roles", "ADMIN")
                .header("X-Real-IP", "203.0.113.99")
                .header(HttpHeaders.ORIGIN, "https://hrm.example.com")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Test-Forwarded-For", "127.0.0.1")
                .expectHeader().valueEquals("X-Test-Forwarded-Proto", "http")
                .expectHeader().valueEquals("X-Test-Untrusted", "false")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://hrm.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui.html", "/v3/api-docs", "/actuator/env", "/internal", "/api/v1/unknown"})
    void unlistedPathsAreDeniedEvenWithValidToken(String path) throws Exception {
        int calls = UPSTREAM_CALLS.get();
        client.get().uri(path).headers(headers -> headers.setBearerAuth(signUnchecked()))
                .exchange().expectStatus().isForbidden();
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(calls);
    }

    @Test
    void healthAndActivationPageArePublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().json("{\"status\":\"UP\"}");
        client.get().uri("/activate").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Test-Backend", "auth");
    }

    @Test
    void preservesBackendAuthorizationFailure() {
        client.get().uri("/api/v1/employees/forbidden").headers(headers -> headers.setBearerAuth(signUnchecked()))
                .exchange().expectStatus().isForbidden().expectHeader().valueEquals("X-Test-Backend", "employee");
    }

    @Test
    void calendarHealthRequiresTokenAndRoutesToWorkforceActuator() {
        int calls = UPSTREAM_CALLS.get();
        client.get().uri("/api/v1/calendar/health-check")
                .exchange().expectStatus().isUnauthorized();
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(calls);

        client.get().uri("/api/v1/calendar/health-check")
                .headers(headers -> headers.setBearerAuth(signUnchecked()))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Test-Backend", "workforce")
                .expectBody(String.class).isEqualTo("/actuator/health|");
    }

    @Test
    void calendarHealthRewriteOnlyAppliesToGet() {
        client.post().uri("/api/v1/calendar/health-check")
                .headers(headers -> headers.setBearerAuth(signUnchecked()))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Test-Backend", "workforce")
                .expectBody(String.class).isEqualTo("/api/v1/calendar/health-check|");
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("42").issuer("auth-service").audience("hrm-api-access")
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("employee_id", "d38e31b7-0bba-420c-8eaf-50edbe5a61ae").claim("roles", List.of("EMPLOYEE"));
    }

    private static String signUnchecked() {
        try {
            return sign(claims(), KEYS, JWSAlgorithm.RS256);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sign(JWTClaimsSet.Builder claims, KeyPair keys, JWSAlgorithm algorithm) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(algorithm), claims.build());
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path publicKey() {
        try {
            var path = Files.createTempFile("gateway-test-public-", ".pem");
            var encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(KEYS.getPublic().getEncoded());
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer backend(String name) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                UPSTREAM_CALLS.incrementAndGet();
                var incoming = exchange.getRequestHeaders();
                var outgoing = exchange.getResponseHeaders();
                outgoing.set("X-Test-Backend", name);
                for (String header : List.of("Authorization", "X-Forwarded-For", "X-Forwarded-Proto")) {
                    if (incoming.getFirst(header) != null) {
                        outgoing.set("X-Test-" + header.replace("X-", ""), incoming.getFirst(header));
                    }
                }
                boolean untrusted = List.of("Forwarded", "X-Forwarded-Host", "X-Real-IP", "X-User-Id", "X-Employee-Id", "X-Roles", "Origin")
                        .stream().anyMatch(incoming::containsKey);
                outgoing.set("X-Test-Untrusted", String.valueOf(untrusted));
                byte[] body = (exchange.getRequestURI() + "|" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("/forbidden") ? 403 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
