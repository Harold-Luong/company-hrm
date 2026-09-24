package com.example.authservice.service;

import com.example.authservice.config.JwtProperties;
import com.example.authservice.config.JwtKeys;
import com.example.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final JwtKeys jwtKeys;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expirationAccess = now.plus(jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("employee_id", user.getEmployeeId())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList())
                .issuer(jwtProperties.getAccessIssuer())
                .audience()
                .add(jwtProperties.getAccessAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expirationAccess))
                .signWith(jwtKeys.getAccess().getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public RefreshTokenResult generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getRefreshTokenExpiration());
        UUID sessionId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .id(sessionId.toString())
                .issuer(jwtProperties.getRefreshIssuer())
                .audience()
                .add(jwtProperties.getRefreshAudience())
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(jwtKeys.getRefresh().getPrivate(), Jwts.SIG.RS256)
                .compact();

        return new RefreshTokenResult(token, sessionId);
    }

    public Claims verifyRefreshToken(String token) {
        Claims claims = parser(jwtKeys.getRefresh().getPublic(),
                jwtProperties.getRefreshIssuer(), jwtProperties.getRefreshAudience())
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get("type", String.class);

        if (!"refresh".equals(type)) {
            throw new IllegalArgumentException(
                    "Invalid refresh token type");
        }
        return claims;
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims parseToken(String token) {
        return parser(jwtKeys.getAccess().getPublic(),
                jwtProperties.getAccessIssuer(), jwtProperties.getAccessAudience())
                .parseSignedClaims(token)
                .getPayload();
    }

    private JwtParser parser(PublicKey key, String issuer, String audience) {
        var builder = Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience);
        var algorithms = builder.sig();
        // JJWT 0.12.6 rejects an empty registry, so retain RS256 while removing the others.
        for (var algorithm : Jwts.SIG.get().values()) {
            if (!Jwts.SIG.RS256.getId().equals(algorithm.getId())) {
                algorithms.remove(algorithm);
            }
        }
        return algorithms.and().build();
    }

    @Getter
    @AllArgsConstructor
    public static class RefreshTokenResult {
        private String token;
        private UUID sessionId;
    }
}
