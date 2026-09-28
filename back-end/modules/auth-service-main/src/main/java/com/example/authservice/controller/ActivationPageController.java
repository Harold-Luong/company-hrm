package com.example.authservice.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivationPageController {
    @GetMapping(value = "/activate", produces = "text/html")
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(new ClassPathResource("static/activation/index.html"));
    }
}
