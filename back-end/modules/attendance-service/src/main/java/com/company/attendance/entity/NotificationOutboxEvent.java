package com.company.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Sự kiện thông báo được lưu cùng transaction nghiệp vụ, chờ gửi tới Kafka. */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
public class NotificationOutboxEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;
}
