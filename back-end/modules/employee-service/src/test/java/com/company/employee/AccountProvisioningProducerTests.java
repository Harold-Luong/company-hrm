package com.company.employee;

import com.company.employee.provisioning.AccountProvisioningProducer;
import com.company.employee.provisioning.EventOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class AccountProvisioningProducerTests {
    @Test
    @SuppressWarnings("unchecked")
    void failedSendIsRetriedAndSuccessfulSendIsMarkedOnlyAfterAcknowledgement() {
        var outbox = mock(EventOutbox.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var message = new EventOutbox.Message(UUID.randomUUID(), "topic", "employee-id", "persisted-payload", UUID.randomUUID());
        var producer = new AccountProvisioningProducer(outbox, kafka);
        when(outbox.claim()).thenReturn(message);
        when(kafka.send(message.topic(), message.messageKey(), message.payload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        producer.publishBatch();
        verify(outbox).retry(message);
        verify(outbox, never()).sent(any());

        when(outbox.claim()).thenReturn(message, null);
        when(kafka.send(message.topic(), message.messageKey(), message.payload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        producer.publishBatch();
        verify(outbox).sent(message);
        verify(kafka, times(2)).send(message.topic(), message.messageKey(), message.payload());
    }
}
