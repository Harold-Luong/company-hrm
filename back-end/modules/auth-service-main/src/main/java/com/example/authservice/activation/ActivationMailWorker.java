package com.example.authservice.activation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.activation.enabled", havingValue = "true")
public class ActivationMailWorker {
    private final ActivationMailQueue queue;
    private final ResendMailSender sender;
    @org.springframework.beans.factory.annotation.Value("${auth.activation.worker-enabled:true}")
    private boolean workerEnabled;
    @Scheduled(fixedDelayString = "${auth.activation.mail-poll-ms:5000}")
    public void deliver() {
        if (!workerEnabled) return;
        for (int i = 0; i < 10; i++) {
            var mail = queue.claim();
            if (mail == null) return;
            var delivery = sender.send(mail);
            if (delivery.id() != null) queue.sent(mail, delivery.id());
            else {
                queue.failed(mail, delivery.error(), delivery.retryable(), delivery.retryAfter());
                return; // Avoid hammering the provider during outages or rate limits.
            }
        }
    }
}
