package com.netpulse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/mock-target")
@Tag(name = "Mock Target", description = "Built-in local synthetic HTTP target for offline testing and chaos demos")
public class MockTargetController {

    @GetMapping
    @Operation(summary = "Built-in healthy endpoint probed by the local mock target")
    public Map<String, Object> health() {
        return Map.of(
                "service", "netpulse-mock-target",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
