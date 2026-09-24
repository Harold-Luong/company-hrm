package com.example.authservice.service;

import com.example.authservice.entity.RefreshSessions;
import com.example.authservice.entity.User;
import com.example.authservice.exception.GlobalException;
import com.example.authservice.repository.RefreshSessionsRepository;
import com.example.authservice.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RefreshSessionsService {

    private final RefreshSessionsRepository refreshSessionsRepository;
    private final UserRepository userRepository;

    @Transactional(Transactional.TxType.MANDATORY)
    public User validateRefreshSession(UUID sessionId, Long userId, String tokenHash) {
        // Always lock the user before the session, including creation and logout-all.
        // The caller holds both locks until rotation/revocation commits or rolls back.
        User user = lockUser(userId);
        RefreshSessions session = refreshSessionsRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new GlobalException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        if (session.getRevokedAt() != null
                || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(Instant.now())
                || !tokenHash.equals(session.getTokenHash())
                || session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new GlobalException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    @Transactional
    public void createRefreshSession(UUID id, User user, String tokenHash, Instant expiresAt) {
        User owner = lockUser(user.getId());
        RefreshSessions session = new RefreshSessions();
        session.setUuid(id);
        session.setUser(owner);
        session.setTokenHash(tokenHash);
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(Instant.now());
        refreshSessionsRepository.save(session);
    }

    @Transactional
    public void revokeRefreshSession(UUID sessionId) {
        RefreshSessions session = refreshSessionsRepository.findById(sessionId)
                .orElseThrow(() -> new GlobalException("Session not found", HttpStatus.NOT_FOUND));
        session.setRevokedAt(Instant.now());
    }

    @Transactional
    public void revokeAllRefreshSessionsByUserId(Long userId) {
        lockUser(userId);
        refreshSessionsRepository.revokeAllByUserId(userId, Instant.now());
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new GlobalException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
    }
}
