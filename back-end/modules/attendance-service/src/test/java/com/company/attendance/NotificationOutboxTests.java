package com.company.attendance;

import com.company.attendance.notifications.NotificationOutbox;
import com.company.attendance.repository.NotificationOutboxRepository;
import com.company.attendance.repository.ScheduleStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance-outbox-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "hrm.notifications.enabled=false"
})
@ActiveProfiles("test")
class NotificationOutboxTests extends JwtTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-19T01:00:00Z");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-000000000043");
    @Autowired NotificationOutboxRepository rows;
    @Autowired ScheduleStateRepository state;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    private KafkaTemplate<String, String> kafka;
    private NotificationOutbox outbox;
    private TransactionTemplate transaction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void reset() {
        kafka = mock(KafkaTemplate.class);
        transaction = new TransactionTemplate(manager);
        rows.deleteAll();
        transaction.executeWithoutResult(status -> state.lockState().setRevision(0));
        // The application publisher is disabled; this instance is invoked explicitly,
        // so no scheduler or Kafka broker can affect the tests.
        outbox = publisher(NOW, true);
    }

    @Test
    void commitsBusinessStateAndOutboxTogetherWithoutSendingKafkaOnTheRequestPath() {
        transaction.executeWithoutResult(status -> {
            state.lockState().setRevision(1);
            append();
        });
        assertThat(state.findById(1).orElseThrow().getRevision()).isEqualTo(1);
        var row = rows.findAll().getFirst();
        assertThat(row.getMessageKey()).isEqualTo(REQUEST.toString());
        assertThat(row.getSentAt()).isNull();
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
        var event = mapper.readValue(row.getPayload(), NotificationOutbox.Event.class);
        assertThat(event).isEqualTo(new NotificationOutbox.Event(row.getEventId(), "ATTENDANCE",
                REQUEST, 3, "42", EMPLOYEE, "99", "APPROVED", NOW.toString()));
        verifyNoInteractions(kafka);
    }

    @Test
    void rollbackRemovesEvenAFlushedOutboxRowAndTheBusinessChange() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            state.lockState().setRevision(1);
            append();
            rows.flush();
            throw new IllegalStateException("Business transaction failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Business transaction failed");
        assertThat(rows.count()).isZero();
        assertThat(state.findById(1).orElseThrow().getRevision()).isZero();
        verifyNoInteractions(kafka);
    }

    @Test
    void requiresBusinessTransactionOnlyWhenEnabled() {
        assertThatThrownBy(this::append).isInstanceOf(IllegalStateException.class);
        var disabled = publisher(NOW, false);
        disabled.append(REQUEST, 3, "42", EMPLOYEE, "99", "APPROVED");
        disabled.publish();
        assertThat(rows.count()).isZero();
        verifyNoInteractions(kafka);
    }

    @Test
    void publishesOnceAndPersistsTheManagedEntityStatus() {
        transaction.executeWithoutResult(status -> append());
        var row = rows.findAll().getFirst();
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        outbox.publish();
        outbox.publish();
        assertThat(rows.findById(row.getEventId()).orElseThrow().getSentAt()).isEqualTo(NOW);
        verify(kafka).send("hrm.requests.notifications.v1", REQUEST.toString(), row.getPayload());
    }

    @Test
    void failedSendRetriesAfterTenSecondsWithTheSameEvent() {
        transaction.executeWithoutResult(status -> append());
        var original = rows.findAll().getFirst();
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        outbox.publish();
        var failed = rows.findById(original.getEventId()).orElseThrow();
        assertThat(failed.getSentAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        publisher(NOW.plusSeconds(9), true).publish();
        verify(kafka).send(anyString(), anyString(), anyString());
        publisher(NOW.plusSeconds(10), true).publish();
        var sent = rows.findById(original.getEventId()).orElseThrow();
        assertThat(sent.getSentAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(sent.getAttempts()).isEqualTo(1);
        verify(kafka, times(2)).send("hrm.requests.notifications.v1", REQUEST.toString(), original.getPayload());
    }

    @Test
    void skipsAnEventLockedByAnotherPublisherThenSendsAfterRelease() throws Exception {
        transaction.executeWithoutResult(status -> append());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(rows.findNextPendingForUpdate(NOW)).isPresent();
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(outbox::publish).get(2, TimeUnit.SECONDS);
                verifyNoInteractions(kafka);
            } finally {
                release.countDown();
            }
            owner.get(5, TimeUnit.SECONDS);
        }
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        outbox.publish();
        assertThat(rows.findAll()).allMatch(row -> row.getSentAt() != null);
        verify(kafka).send(anyString(), anyString(), anyString());
    }

    @Test
    void processesAtMostTwentyEventsPerScheduledRun() {
        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < 21; i++) append();
        });
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        outbox.publish();
        assertThat(rows.findAll().stream().filter(row -> row.getSentAt() != null).count()).isEqualTo(20);
        assertThat(rows.findAll().stream().filter(row -> row.getSentAt() == null).count()).isEqualTo(1);
        verify(kafka, times(20)).send(anyString(), anyString(), anyString());
    }

    private NotificationOutbox publisher(Instant now, boolean enabled) {
        return new NotificationOutbox(rows, kafka, manager, mapper, Clock.fixed(now, ZoneOffset.UTC), enabled);
    }

    private void append() {
        outbox.append(REQUEST, 3, "42", EMPLOYEE, "99", "APPROVED");
    }
}
