package com.netpulse.controller;

import com.netpulse.domain.IncidentStatus;
import com.netpulse.dto.IncidentResponse;
import com.netpulse.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Query active and historical anomaly incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    @Operation(summary = "List incidents filtered optionally by status and/or targetId")
    public List<IncidentResponse> listIncidents(@RequestParam(required = false) IncidentStatus status,
                                                @RequestParam(required = false) Long targetId) {
        return incidentService.findIncidents(status, targetId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident details by ID")
    public IncidentResponse getIncident(@PathVariable Long id) {
        return incidentService.findById(id);
    }
}
