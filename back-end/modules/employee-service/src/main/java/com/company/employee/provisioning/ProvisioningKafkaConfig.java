package com.company.employee.provisioning;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hrm.events.enabled", havingValue = "true")
public class ProvisioningKafkaConfig {
    @Bean
    DefaultErrorHandler provisioningErrorHandler() {
        // Until DLT operations are implemented, never silently skip a business message.
        var handler = new DefaultErrorHandler(new FixedBackOff(5000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(java.util.Map.of(Exception.class, true), true);
        handler.setAckAfterHandle(false);
        return handler;
    }
}
