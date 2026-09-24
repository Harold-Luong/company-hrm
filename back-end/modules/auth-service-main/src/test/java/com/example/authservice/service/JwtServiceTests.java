package com.example.authservice.service;

import com.example.authservice.config.JwtKeys;
import com.example.authservice.config.JwtProperties;
import com.example.authservice.entity.User;
import com.example.authservice.enums.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    private JwtProperties properties;
    private JwtKeys keys;
    private JwtService service;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setAccessPrivateKey(new ClassPathResource("jwt/access-private.pem"));
        properties.setAccessPublicKey(new ClassPathResource("jwt/access-public.pem"));
        properties.setRefreshPrivateKey(new ClassPathResource("jwt/refresh-private.pem"));
        properties.setRefreshPublicKey(new ClassPathResource("jwt/refresh-public.pem"));
        properties.setAccessIssuer("auth-service");
        properties.setRefreshIssuer("auth-service");
        properties.setAccessAudience("hrm-api-access");
        properties.setRefreshAudience("hrm-api-refresh");
        properties.setAccessTokenExpiration(Duration.ofMinutes(15));
        properties.setRefreshTokenExpiration(Duration.ofDays(7));
        keys = new JwtKeys(properties);
        service = new JwtService(properties, keys);
        user = new User();
        user.setId(1L);
        user.setEmployeeId(UUID.fromString("550e8400-e29b-41d4-a716-000000001001"));
        user.setEmail("employee@example.com");
        user.setRoles(Set.of(UserRole.HR, UserRole.EMPLOYEE));
    }

    @Test
    void accessTokenCanBeVerifiedWithOnlyPublicKey() {
        String token = service.generateAccessToken(user);
        var parsed = Jwts.parser().verifyWith(keys.getAccess().getPublic()).build().parseSignedClaims(token);
        assertEquals("RS256", parsed.getHeader().getAlgorithm());
        var claims = parsed.getPayload();
        assertEquals("1", claims.getSubject());
        assertEquals(user.getEmployeeId().toString(), claims.get("employee_id", String.class));
        assertEquals(user.getEmail(), claims.get("email", String.class));
        assertEquals(List.of("EMPLOYEE", "HR"), claims.get("roles", List.class));
        assertEquals(properties.getAccessIssuer(), claims.getIssuer());
        assertEquals(Set.of(properties.getAccessAudience()), claims.getAudience());
        assertEquals(properties.getAccessTokenExpiration(),
                Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
        assertTrue(service.validateToken(token));
    }

    @Test
    void refreshTokenUsesItsOwnRsaKeyAndRetainsSessionClaims() {
        var result = service.generateRefreshToken(user);
        var parsed = Jwts.parser().verifyWith(keys.getRefresh().getPublic()).build()
                .parseSignedClaims(result.getToken());
        assertEquals("RS256", parsed.getHeader().getAlgorithm());
        var claims = service.verifyRefreshToken(result.getToken());
        assertEquals("1", claims.getSubject());
        assertEquals("refresh", claims.get("type", String.class));
        assertEquals(result.getSessionId().toString(), claims.getId());
        assertEquals(properties.getRefreshIssuer(), claims.getIssuer());
        assertEquals(Set.of(properties.getRefreshAudience()), claims.getAudience());
        assertEquals(properties.getRefreshTokenExpiration(),
                Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
        assertFalse(service.validateToken(result.getToken()));
        assertThrows(JwtException.class, () -> service.verifyRefreshToken(service.generateAccessToken(user)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access", "refresh"})
    void rejectsWrongKeyAlgorithmsClaimsAndUnsignedTokens(String purpose) {
        boolean access = "access".equals(purpose);
        var pair = access ? keys.getAccess() : keys.getRefresh();
        for (String scenario : List.of("wrong-key", "HS256", "RS512", "unsigned", "expired", "issuer", "audience", "tampered")) {
            var builder = Jwts.builder().subject("1").claim("type", "refresh")
                    .issuer("issuer".equals(scenario) ? "other" : "auth-service")
                    .audience().add("audience".equals(scenario) ? "other" : "hrm-api-" + purpose).and()
                    .expiration(Date.from(Instant.now().plusSeconds("expired".equals(scenario) ? -60 : 60)));
            String token = switch (scenario) {
                case "wrong-key" -> builder.signWith((access ? keys.getRefresh() : keys.getAccess()).getPrivate(), Jwts.SIG.RS256).compact();
                case "HS256" -> builder.signWith(Jwts.SIG.HS256.key().build()).compact();
                case "RS512" -> builder.signWith(pair.getPrivate(), Jwts.SIG.RS512).compact();
                case "unsigned" -> builder.compact();
                default -> builder.signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
            };
            if ("tampered".equals(scenario)) {
                String[] parts = token.split("\\.");
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("\"sub\":\"1\"", "\"sub\":\"2\"");
                token = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
            }
            String invalidToken = token;
            if (access) {
                assertFalse(service.validateToken(invalidToken), scenario);
            } else {
                assertThrows(JwtException.class, () -> service.verifyRefreshToken(invalidToken), scenario);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unreadable", "malformed", "mismatched", "shared", "weak"})
    void rejectsInvalidKeyConfigurationAtStartup(String scenario) throws Exception {
        switch (scenario) {
            case "missing" -> properties.setAccessPrivateKey(null);
            case "unreadable" -> properties.setAccessPrivateKey(new ClassPathResource("jwt/missing.pem"));
            case "malformed" -> properties.setAccessPrivateKey(new ByteArrayResource("invalid".getBytes(StandardCharsets.US_ASCII)));
            case "mismatched" -> properties.setAccessPublicKey(properties.getRefreshPublicKey());
            case "shared" -> {
                properties.setRefreshPrivateKey(properties.getAccessPrivateKey());
                properties.setRefreshPublicKey(properties.getAccessPublicKey());
            }
            case "weak" -> {
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(1024);
                var pair = generator.generateKeyPair();
                properties.setAccessPrivateKey(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
                properties.setAccessPublicKey(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            }
        }
        Class<? extends RuntimeException> expected = switch (scenario) {
            case "missing", "shared" -> IllegalArgumentException.class;
            default -> IllegalStateException.class;
        };
        assertThrows(expected, () -> new JwtKeys(properties));
    }

    private ByteArrayResource pem(String type, byte[] encoded) {
        return new ByteArrayResource(("-----BEGIN " + type + "-----\n"
                + Base64.getEncoder().encodeToString(encoded) + "\n-----END " + type + "-----\n")
                .getBytes(StandardCharsets.US_ASCII));
    }
}
