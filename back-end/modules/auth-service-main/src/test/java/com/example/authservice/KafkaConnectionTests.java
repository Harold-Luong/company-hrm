package com.example.authservice;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in integration check against local Kafka; no controller, database or JWT keys required. */
@EnabledIfEnvironmentVariable(named = "KAFKA_SMOKE_TEST", matches = "true")
class KafkaConnectionTests {

    @Test
    @SuppressWarnings("unchecked")
    void applicationKafkaConfigurationCanSendAndReceive() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withInitializer(context -> {
                    // Load the real service configuration, not the H2 application.yaml from tests.
                    try {
                        var sources = new YamlPropertySourceLoader().load("service-application",
                                new FileSystemResource("src/main/resources/application.yaml"));
                        sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (IOException exception) {
                        throw new IllegalStateException("Cannot load service Kafka configuration", exception);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var kafkaAdmin = context.getBean(KafkaAdmin.class);
                    try (var admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
                        assertThat(admin.listTopics().names().get(15, TimeUnit.SECONDS))
                                .contains("hrm.employee.account-requests.v1", "hrm.auth.account-results.v1");
                    }

                    KafkaTemplate<String, String> template = context.getBean(KafkaTemplate.class);
                    ConsumerFactory<String, String> factory = context.getBean(ConsumerFactory.class);
                    String marker = "auth-service-main-" + UUID.randomUUID();
                    var sent = template.send("hrm.local.smoke.v1", marker, marker)
                            .get(20, TimeUnit.SECONDS).getRecordMetadata();
                    var partition = new TopicPartition(sent.topic(), sent.partition());
                    try (var consumer = factory.createConsumer("smoke-" + marker, "smoke-" + marker)) {
                        consumer.assign(List.of(partition));
                        consumer.seek(partition, sent.offset());
                        boolean found = false;
                        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                        while (!found && System.nanoTime() < deadline) {
                            for (var record : consumer.poll(Duration.ofSeconds(1))) {
                                if (marker.equals(record.key()) && marker.equals(record.value())) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                        assertThat(found).as("Message sent through this service's Kafka configuration").isTrue();
                    }
                });
    }
}
