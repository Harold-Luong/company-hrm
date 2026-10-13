package com.company.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class AccountResultConsumerTests {
    static class Capture extends AccountResults {
        Event event;
        Capture() { super(null); }
        @Override public void accept(Event value) { event = value; }
    }
    @Test
    void readsAuthPayloadAndChecksKafkaEmployeeKey() {
        var capture = new Capture();
        var consumer = new AccountResultConsumer(capture, JsonMapper.builder().build());
        var employeeId = "550e8400-e29b-41d4-a716-446655440000";
        var payload = """
            {"eventId":"650e8400-e29b-41d4-a716-446655440000","eventType":"AccountCreated",
             "schemaVersion":1,"occurredAt":"2026-10-13T01:00:00Z","producer":"auth-service",
             "correlationId":"750e8400-e29b-41d4-a716-446655440000",
             "requestId":"850e8400-e29b-41d4-a716-446655440000",
             "data":{"employeeId":"550e8400-e29b-41d4-a716-446655440000",
                     "accountStatus":"PENDING_ACTIVATION","accountVersion":1,"requestedBy":"42"}}
            """;
        consumer.receive(new ConsumerRecord<>("hrm.auth.account-results.v1", 0, 0, employeeId, payload));
        assertThat(capture.event.data().requestedBy()).isEqualTo("42");
        assertThat(capture.event.occurredAt()).isEqualTo(java.time.Instant.parse("2026-10-13T01:00:00Z"));
        assertThatThrownBy(() -> consumer.receive(new ConsumerRecord<>("hrm.auth.account-results.v1", 0, 1, "another", payload)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
