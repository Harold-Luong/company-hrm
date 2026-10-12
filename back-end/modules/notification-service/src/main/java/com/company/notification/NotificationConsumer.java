package com.company.notification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;
@Configuration
@ConditionalOnProperty(name="hrm.notifications.enabled", havingValue="true")
public class NotificationConsumer {
 private final Notifications notifications;
 private final ObjectMapper mapper;
 public NotificationConsumer(Notifications notifications, ObjectMapper mapper) { this.notifications=notifications; this.mapper=mapper; }
 @KafkaListener(topics="hrm.requests.notifications.v1")
 public void receive(String payload) { notifications.accept(mapper.readValue(payload, Notifications.Event.class)); }
 // Never acknowledge failed persistence or silently discard an event.
 @Bean DefaultErrorHandler notificationErrorHandler() {
   var handler = new DefaultErrorHandler(new FixedBackOff(3000, FixedBackOff.UNLIMITED_ATTEMPTS));
   handler.setClassifications(java.util.Map.of(), true);
   return handler;
 }
}
