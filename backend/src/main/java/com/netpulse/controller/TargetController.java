package com.netpulse.controller;

import com.netpulse.dto.ProbeResultResponse;
import com.netpulse.dto.TargetRequest;
import com.netpulse.dto.TargetResponse;
import com.netpulse.dto.TracerouteResponse;
import com.netpulse.prober.ProbeOrchestrator;
import com.netpulse.prober.TracerouteService;
import com.netpulse.service.TargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/targets")
@Tag(name = "Targets", description = "CRUD management and telemetry lookup for monitored endpoints")
public class TargetController {

    private final TargetService targetService;
    private final ProbeOrchestrator probeOrchestrator;
    private final TracerouteService tracerouteService;

    public TargetController(TargetService targetService,
                            ProbeOrchestrator probeOrchestrator,
                            TracerouteService tracerouteService) {
        this.targetService = targetService;
        this.probeOrchestrator = probeOrchestrator;
        this.tracerouteService = tracerouteService;
    }

    @GetMapping
    @Operation(summary = "List all monitored targets")
    public List<TargetResponse> listTargets() {
        return targetService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a monitored target by ID")
    public TargetResponse getTarget(@PathVariable Long id) {
        return targetService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new monitored target")
    public ResponseEntity<TargetResponse> createTarget(@Valid @RequestBody TargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(targetService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing monitored target")
    public TargetResponse updateTarget(@PathVariable Long id, @Valid @RequestBody TargetRequest request) {
        return targetService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Pause or resume monitoring for a target")
    public TargetResponse toggleTarget(@PathVariable Long id) {
        return targetService.toggleEnabled(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a monitored target and its associated history")
    public ResponseEntity<Void> deleteTarget(@PathVariable Long id) {
        targetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/probes")
    @Operation(summary = "Get recent probe results for a target")
    public List<ProbeResultResponse> getTargetProbes(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return targetService.findRecentProbes(id, limit);
    }

    @PostMapping("/{id}/probe")
    @Operation(summary = "Trigger an immediate probe execution for a target")
    public ProbeResultResponse triggerProbe(@PathVariable Long id) {
        return probeOrchestrator.triggerProbeNow(id);
    }

    @GetMapping("/{id}/traceroute")
    @Operation(summary = "Run OS-aware hop-by-hop traceroute with deterministic simulated fallback")
    public TracerouteResponse traceTarget(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean simulate) {
        return tracerouteService.traceTarget(id, simulate);
    }
}
