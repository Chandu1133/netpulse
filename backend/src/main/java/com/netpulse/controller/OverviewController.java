package com.netpulse.controller;

import com.netpulse.dto.OverviewResponse;
import com.netpulse.service.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
@Tag(name = "Overview", description = "Global fleet health and telemetry summary")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    @Operation(summary = "Get global target health counts, average latency, and open incident count")
    public OverviewResponse getOverview() {
        return overviewService.getOverview();
    }
}
