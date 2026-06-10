package com.springai.chatsys.controller;

import com.springai.chatsys.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Map.of("service", "spring-ai-chat-sys"));
    }
}
