package com.company.attendance.security;

import com.company.attendance.config.AttendanceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.company.attendance.service.ApiRules.error;

@Component
public class CorporateNetwork {
    private final List<IpAddressMatcher> allowed;
    private final List<IpAddressMatcher> proxies;
    public CorporateNetwork(AttendanceProperties properties) {
        allowed = matchers(properties.allowedNetworks()); proxies = matchers(properties.trustedProxies());
    }
    public String requireAllowed(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (matches(proxies, ip)) {
            // Gateway replaces all untrusted forwarding headers with exactly its TCP peer.
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded == null || forwarded.contains(",") || !forwarded.matches("[0-9a-fA-F:.]+"))
                throw error(HttpStatus.FORBIDDEN, "A trusted single client IP is required");
            ip = forwarded;
        }
        if (!matches(allowed, ip)) throw error(HttpStatus.FORBIDDEN, "Check-in/out is only allowed from the company network");
        return ip;
    }
    private List<IpAddressMatcher> matchers(List<String> networks) {
        return networks == null ? List.of() : networks.stream().filter(s -> !s.isBlank()).map(IpAddressMatcher::new).toList();
    }
    private boolean matches(List<IpAddressMatcher> networks, String ip) {
        if (ip == null) return false;
        try { return networks.stream().anyMatch(network -> network.matches(ip)); }
        catch (IllegalArgumentException exception) { return false; }
    }
}
