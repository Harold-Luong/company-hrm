package com.company.notification;

import com.company.notification.exception.ExceptionMessages;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="hrm.notifications.enabled", havingValue="true")
public class AccountResultConsumer {
    private final AccountResults results;
    private final ObjectMapper mapper;
    public AccountResultConsumer(AccountResults results, ObjectMapper mapper) {
        this.results = results;
        this.mapper = mapper;
    }
    @KafkaListener(topics="${hrm.notifications.account-result-topic:hrm.auth.account-results.v1}",
            groupId="hrm-notification-account-results-v1")
    public void receive(ConsumerRecord<String, String> record) {
        var event = mapper.readValue(record.value(), AccountResults.Event.class);
        if (event == null || event.data() == null || event.data().employeeId() == null
                || !event.data().employeeId().toString().equals(record.key()))
            throw new IllegalArgumentException(ExceptionMessages.ACCOUNT_RESULT_KEY_MISMATCH);
        results.accept(event);
    }
}
