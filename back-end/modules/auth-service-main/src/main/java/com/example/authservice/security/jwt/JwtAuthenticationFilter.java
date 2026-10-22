package com.example.authservice.security.jwt;

import com.example.authservice.exception.ExceptionMessages;
import com.example.authservice.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import com.example.authservice.service.JwtService;
import com.example.authservice.entity.User;
import com.example.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/api/v1/auth/refresh".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtService.parseToken(token);
            if (claims.getExpiration() == null) {
                throw new IllegalArgumentException(ExceptionMessages.ACCESS_TOKEN_EXPIRATION_REQUIRED);
            }
            String userId = claims.getSubject();
            User user = userRepository.findById(Long.valueOf(userId))
                    .orElseThrow(() -> new IllegalArgumentException(ExceptionMessages.ACCOUNT_NO_LONGER_EXISTS));
            String sid = claims.get("sid", String.class);
            if (sid == null || !UUID.fromString(sid).equals(user.getActiveSessionId())) {
                throw new IllegalArgumentException(ExceptionMessages.SESSION_REPLACED);
            }
            if (!user.isActive() || user.isActivationPending() || user.isAdministrativelyLocked()) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(
                        new ErrorResponse("403", ExceptionMessages.USER_INACTIVE)));
                return;
            }
            Set<GrantedAuthority> authorities = user.getRoles().stream()
                    .map(role -> "ROLE_" + role.name())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authorities);
            authentication.setDetails(UUID.fromString(sid));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("message", ExceptionMessages.INVALID_ACCESS_TOKEN)));

            return;
        }
        filterChain.doFilter(request, response);
    }
}
