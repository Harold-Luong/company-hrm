package com.company.notification;

import com.company.notification.security.SessionVerifier;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.mockito.Mockito.*;

class NotificationSessionTests {
    @ParameterizedTest
    @ValueSource(strings = {"invalid_token", "temporarily_unavailable"})
    void anOpenStreamDoesNotReadOrPublishMoreDataAfterSessionVerificationFails(String code) {
        var notifications = mock(Notifications.class);
        var results = mock(AccountResults.class);
        var sessions = mock(SessionVerifier.class);
        var streams = new NotificationStreams(notifications, results, sessions);
        var jwt = Jwt.withTokenValue("test-access").header("alg", "RS256")
                .subject("42").expiresAt(Instant.now().plusSeconds(300)).build();
        try {
            streams.open(new JwtAuthenticationToken(jwt), null);
            doThrow(new OAuth2AuthenticationException(new OAuth2Error(code)))
                    .when(sessions).verify("test-access");
            streams.tick();
            verify(sessions, timeout(2000)).verify("test-access");
            verifyNoInteractions(notifications, results);
        } finally {
            streams.close();
        }
    }
}
