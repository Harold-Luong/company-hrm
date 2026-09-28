package com.company.employee.provisioning;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hrm.events.enabled", havingValue = "true")
public class AccountResultConsumer {
    private final ObjectMapper mapper;
    private final AccountRequestService service;
    private final AccountLifecycleService lifecycle;

    @KafkaListener(topics = "${hrm.events.result-topic:hrm.auth.account-results.v1}",
            groupId = "${hrm.events.result-group:employee-account-results-v1}",
            autoStartup = "${hrm.events.listeners-enabled:true}")
    public void receive(ConsumerRecord<String, String> record) {
        var event = mapper.readValue(record.value(), AccountProvisioningResult.class);
        if (event.data() == null || event.data().employeeId() == null
                || !event.data().employeeId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Account result key does not match employeeId");
        }
        service.apply(event);
    }

    @KafkaListener(topics = "${hrm.events.lifecycle-topic:hrm.auth.account-lifecycle.v1}",
            groupId = "${hrm.events.lifecycle-group:employee-account-lifecycle-v1}",
            autoStartup = "${hrm.events.listeners-enabled:true}")
    public void receiveLifecycle(ConsumerRecord<String, String> record) {
        var event = mapper.readValue(record.value(), AccountLifecycleService.Event.class);
        if (event == null || event.data() == null || event.data().employeeId() == null
                || !event.data().employeeId().toString().equals(record.key()))
            throw new IllegalArgumentException("Account lifecycle key does not match employeeId");
        lifecycle.apply(event);
    }
}
