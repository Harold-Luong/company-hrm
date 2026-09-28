package com.example.authservice.provisioning;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hrm.events.enabled", havingValue = "true")
public class AccountRequestConsumer {
    private final ObjectMapper mapper;
    private final AccountProvisioningService service;

    @KafkaListener(topics = "${hrm.events.request-topic:hrm.employee.account-requests.v1}",
            groupId = "${hrm.events.request-group:auth-account-requests-v1}",
            autoStartup = "${hrm.events.listeners-enabled:true}")
    public void receive(ConsumerRecord<String, String> record) {
        var event = mapper.readValue(record.value(), EmployeeAccountRequested.class);
        if (event.data() == null || event.data().employeeId() == null
                || !event.data().employeeId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Account request key does not match employeeId");
        }
        service.process(event);
    }
}
