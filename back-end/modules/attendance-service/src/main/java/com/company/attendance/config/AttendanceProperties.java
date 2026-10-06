package com.company.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("attendance")
public record AttendanceProperties(String employeeUrl, String leaveUrl, String calendarUrl,
                                   List<String> allowedNetworks, List<String> trustedProxies) {}
