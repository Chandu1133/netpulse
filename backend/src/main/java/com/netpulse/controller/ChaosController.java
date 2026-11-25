package com.netpulse.controller;

import com.netpulse.dto.ChaosRuleRequest;
import com.netpulse.dto.ChaosRuleResponse;
import com.netpulse.service.ChaosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chaos")
@Tag(name = "Chaos Simulator", description = "Inject or clear synthetic latency, jitter, packet loss, and failure rules")
public class ChaosController {

    private final ChaosService chaosService;

    public ChaosController(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @GetMapping
    @Operation(summary = "List all active chaos rules")
    public List<ChaosRuleResponse> listActiveRules() {
        return chaosService.findAllActive();
    }

    @GetMapping("/{targetId}")
    @Operation(summary = "Get active chaos rule for a target")
    public ChaosRuleResponse getRule(@PathVariable Long targetId) {
        return chaosService.findByTargetId(targetId);
    }

    @PostMapping("/{targetId}")
    @Operation(summary = "Create or update chaos fault-injection rule for a target")
    public ChaosRuleResponse upsertRule(@PathVariable Long targetId,
                                        @Valid @RequestBody ChaosRuleRequest request) {
        return chaosService.upsertRule(targetId, request);
    }

    @DeleteMapping("/{targetId}")
    @Operation(summary = "Clear chaos fault-injection rule for a target")
    public ResponseEntity<Void> clearRule(@PathVariable Long targetId) {
        chaosService.clearRule(targetId);
        return ResponseEntity.noContent().build();
    }
}
