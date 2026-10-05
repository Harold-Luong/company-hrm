package com.api.gateway.security;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Locale;

/** Only the TCP peer at this public edge may supply the downstream client IP. */
@Component
public class EdgeHeadersFilter implements HttpHeadersFilter, Ordered {
    @Override
    public int getOrder() {
        // Generate trusted values after Gateway removes forwarding/hop-by-hop headers.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        var peer = exchange.getRequest().getRemoteAddress();
        var headers = new HttpHeaders();
        headers.addAll(input);
        var untrusted = headers.headerNames().stream().filter(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.equals("forwarded") || lower.startsWith("x-forwarded-")
                    || lower.equals("x-real-ip") || lower.equals("x-user-id")
                    || lower.equals("x-employee-id") || lower.equals("x-roles");
        }).toList();
        untrusted.forEach(headers::remove);
        if (peer != null && peer.getAddress() != null) {
            headers.set("X-Forwarded-For", peer.getAddress().getHostAddress());
        }
        headers.set("X-Forwarded-Proto", exchange.getRequest().getURI().getScheme());
        return headers;
    }
}
