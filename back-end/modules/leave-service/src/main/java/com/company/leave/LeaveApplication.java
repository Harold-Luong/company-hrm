package com.company.leave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class LeaveApplication {
    public static void main(String[] args) { SpringApplication.run(LeaveApplication.class, args); }

    @Bean
    Clock leaveClock() { return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")); }
}
