package com.company.employee.provisioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("${hrm.events.enabled:false} and ${hrm.events.publisher-enabled:true}")
public class AccountProvisioningProducer {
    private final EventOutbox outbox;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelayString = "${hrm.events.publish-delay-ms:1000}")
    public void publishBatch() {
        for (int i = 0; i < 20; i++) {
            var message = outbox.claim();
            if (message == null) return;
            try {
                kafka.send(message.topic(), message.messageKey(), message.payload()).get(10, TimeUnit.SECONDS);
                outbox.sent(message);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                outbox.retry(message);
                return;
            } catch (Exception exception) {
                // Do not log the payload or potentially sensitive exception messages.
                log.warn("Outbox event {} will retry ({})", message.eventId(), exception.getClass().getSimpleName());
                outbox.retry(message);
                return;
            }
        }
    }
}
