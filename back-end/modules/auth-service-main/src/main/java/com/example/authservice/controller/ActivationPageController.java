package com.example.authservice.controller;

import com.example.authservice.activation.ActivationSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class ActivationPageController {
    private final ActivationSettings settings;

    // Preserve links from previously sent invitations; Vue owns the activation UI.
    @GetMapping({"/activate", "/activation/index.html"})
    public ResponseEntity<Void> page(@RequestParam(required = false) String token) {
        var destination = UriComponentsBuilder.fromUriString(settings.frontendUrl);
        if (token != null && token.matches("[A-Za-z0-9_-]{43}")) {
            destination.queryParam("token", token);
        }
        return ResponseEntity.status(302)
                .location(destination.build().toUri())
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
