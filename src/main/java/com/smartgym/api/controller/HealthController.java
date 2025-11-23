package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Instant START_TIME = Instant.now();

    @GetMapping
    public ApiResponse<?> health() {
        var uptime = Duration.between(START_TIME, Instant.now()).toSeconds();
        var payload = Map.of(
                "status", "UP",
                "startedAt", START_TIME.toString(),
                "uptimeSeconds", uptime
        );
        return ApiResponse.ok(payload, "Health check OK", Instant.now().toString(), "/api/v1/health");
    }
}