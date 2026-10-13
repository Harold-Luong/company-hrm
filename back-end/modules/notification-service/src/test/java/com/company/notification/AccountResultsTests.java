package com.company.notification;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

class AccountResultsTests {
    JdbcTemplate jdbc;
    AccountResults results;
    int locks;
    SingleConnectionDataSource source;
    final Notifications.Actor hr = new Notifications.Actor("hr-1", true);

    @BeforeEach
    void prepare() throws Exception {
        source = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/sql/002_account_result_notifications.sql"));
        }
        // H2 covers persistence/filtering but does not implement PostgreSQL's transaction table lock.
        jdbc = new JdbcTemplate(source) {
            @Override public void execute(String sql) {
                if (sql.equals("LOCK TABLE account_result_notifications IN EXCLUSIVE MODE")) locks++;
                else super.execute(sql);
            }
        };
        results = new AccountResults(jdbc);
    }

    @AfterEach void close() { source.destroy(); }

    @Test
    void deliversSuccessAndFailureOnlyToRequesterAndDeduplicatesRequestAndEvent() {
        var event = event("hr-1", false);
        results.accept(event);
        results.accept(event);
        results.accept(new AccountResults.Event(UUID.randomUUID(), event.eventType(), 1, event.occurredAt(),
                event.producer(), event.correlationId(), event.requestId(), event.data()));
        results.accept(event("hr-2", false));
        results.accept(event("hr-1", true));
        var batch = results.after(hr, 0);
        assertThat(batch.items()).extracting(AccountResults.Item::provisioningStatus).containsExactly("SUCCEEDED", "FAILED");
        assertThat(batch.items().getFirst().employeeId()).isEqualTo(event.data().employeeId());
        assertThat(batch.items().getLast().errorCode()).isEqualTo("EMAIL_ALREADY_USED");
        assertThat(results.after(new Notifications.Actor("hr-3", true), 0).items()).isEmpty();
        assertThat(results.after(new Notifications.Actor("hr-1", false), 0).items()).isEmpty();
        assertThat(results.after(hr, batch.cursor()).items()).isEmpty();
        assertThat(results.latest(hr)).isEqualTo(batch.cursor());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_result_notifications", Integer.class)).isEqualTo(3);
        assertThat(locks).isEqualTo(5);
    }

    @Test
    void replaysAllResultsInBatchesWithoutSkippingOtherRecipients() {
        for (int i = 0; i < 53; i++) results.accept(event("hr-1", false));
        var first = results.after(hr, 0);
        var second = results.after(hr, first.cursor());
        assertThat(first.items()).hasSize(50);
        assertThat(second.items()).hasSize(3);
        assertThat(results.after(hr, second.cursor()).items()).isEmpty();
    }

    @Test
    void skipsLegacyEventsWithoutRecipientAndRejectsInvalidEnvelopes() {
        results.accept(event(null, false));
        assertThat(results.latest(hr)).isZero();
        var event = event("hr-1", false);
        assertThatThrownBy(() -> results.accept(new AccountResults.Event(event.eventId(), event.eventType(), 1,
                event.occurredAt(), "employee-service", event.correlationId(), event.requestId(), event.data())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AccountResults.Event event(String recipient, boolean failure) {
        return new AccountResults.Event(UUID.randomUUID(), failure ? "AccountCreationFailed" : "AccountCreated", 1,
                Instant.now(), "auth-service", UUID.randomUUID(), UUID.randomUUID(),
                new AccountResults.Data(UUID.randomUUID(), failure ? null : "PENDING_ACTIVATION", failure ? null : 1L,
                        failure ? "EMAIL_ALREADY_USED" : null, recipient));
    }
}
