package com.company.attendance.config;

import com.company.attendance.service.DefaultScheduleInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultScheduleConfig {
    @Bean
    @ConditionalOnProperty(name = "attendance.initialize-default-schedule", havingValue = "true", matchIfMissing = true)
    ApplicationRunner initializeDefaultSchedule(DefaultScheduleInitializer initializer) {
        return args -> initializer.initialize();
    }
}
