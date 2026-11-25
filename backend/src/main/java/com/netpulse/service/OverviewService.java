package com.netpulse.service;

import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.TargetStatus;
import com.netpulse.dto.OverviewResponse;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.IncidentRepository;
import com.netpulse.repository.MonitorTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalDouble;

@Service
public class OverviewService {

    private final MonitorTargetRepository targetRepository;
    private final IncidentRepository incidentRepository;
    private final ChaosRuleRepository chaosRuleRepository;

    public OverviewService(MonitorTargetRepository targetRepository,
                           IncidentRepository incidentRepository,
                           ChaosRuleRepository chaosRuleRepository) {
        this.targetRepository = targetRepository;
        this.incidentRepository = incidentRepository;
        this.chaosRuleRepository = chaosRuleRepository;
    }

    @Transactional(readOnly = true)
    public OverviewResponse getOverview() {
        List<MonitorTarget> targets = targetRepository.findAll();

        long total = targets.size();
        long active = targets.stream().filter(MonitorTarget::isEnabled).count();
        long healthy = targets.stream().filter(t -> t.getStatus() == TargetStatus.HEALTHY).count();
        long degraded = targets.stream().filter(t -> t.getStatus() == TargetStatus.DEGRADED).count();
        long down = targets.stream().filter(t -> t.getStatus() == TargetStatus.DOWN).count();
        long paused = targets.stream().filter(t -> t.getStatus() == TargetStatus.PAUSED).count();
        long unknown = targets.stream().filter(t -> t.getStatus() == TargetStatus.UNKNOWN).count();

        OptionalDouble avgLatency = targets.stream()
                .filter(MonitorTarget::isEnabled)
                .map(MonitorTarget::getLastTotalLatencyMs)
                .filter(v -> v != null && v >= 0.0)
                .mapToDouble(Double::doubleValue)
                .average();

        Double roundedAvg = avgLatency.isPresent()
                ? Math.round(avgLatency.getAsDouble() * 100.0) / 100.0
                : null;

        long openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN);
        long activeChaos = chaosRuleRepository.findByActiveTrue().stream()
                .filter(ChaosRule::hasAnyFault)
                .count();

        return new OverviewResponse(
                total,
                active,
                healthy,
                degraded,
                down,
                paused,
                unknown,
                roundedAvg,
                openIncidents,
                activeChaos
        );
    }
}
