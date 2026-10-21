package com.api.gateway.config;

import java.util.List;

/**
 * Các đường dẫn theo chính sách truy cập; quyền và HTTP method đặt tại
 * SecurityConfig.
 */
final class SecurityPaths {
    private SecurityPaths() {
    }

    // Chain riêng để access token hết hạn không chặn việc refresh.
    static final String REFRESH = "/api/v1/auth/refresh";

    static final List<String> PUBLIC_GET = List.of(
            "/actuator/health",
            "/api/v1/auth/csrf",
            "/activate",
            "/activation/index.html");

    static final List<String> PUBLIC_POST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/activate");

    // Gateway yêu cầu xác thực; từng service kiểm tra quyền nghiệp vụ chi tiết.
    static final List<String> AUTHENTICATED = List.of(
            "/api/v1/auth/**",
            "/api/v1/employees/**",
            "/api/v1/departments/**",
            "/api/v1/positions/**",
            "/api/v1/calendar/**",
            "/api/v1/calendar-events/**",
            "/api/v1/leave/**",
            "/api/v1/attendance/**",
            "/api/v1/notifications",
            "/api/v1/notifications/**");
}
