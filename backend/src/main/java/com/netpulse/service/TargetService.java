package com.netpulse.service;

import com.netpulse.anomaly.SlidingWindowCalculator;
import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.TargetStatus;
import com.netpulse.dto.ProbeResultResponse;
import com.netpulse.dto.TargetRequest;
import com.netpulse.dto.TargetResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.IncidentRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TargetService {

    private final MonitorTargetRepository targetRepository;
    private final ProbeResultRepository probeResultRepository;
    private final IncidentRepository incidentRepository;
    private final ChaosRuleRepository chaosRuleRepository;
    private final SlidingWindowCalculator slidingWindowCalculator;

    public TargetService(MonitorTargetRepository targetRepository,
                         ProbeResultRepository probeResultRepository,
                         IncidentRepository incidentRepository,
                         ChaosRuleRepository chaosRuleRepository,
                         SlidingWindowCalculator slidingWindowCalculator) {
        this.targetRepository = targetRepository;
        this.probeResultRepository = probeResultRepository;
        this.incidentRepository = incidentRepository;
        this.chaosRuleRepository = chaosRuleRepository;
        this.slidingWindowCalculator = slidingWindowCalculator;
    }

    @Transactional(readOnly = true)
    public List<TargetResponse> findAll() {
        Set<Long> chaosTargetIds = chaosRuleRepository.findByActiveTrue().stream()
                .filter(ChaosRule::hasAnyFault)
                .map(rule -> rule.getTarget().getId())
                .collect(Collectors.toSet());

        return targetRepository.findAllByOrderByIdAsc().stream()
                .map(target -> toResponse(target, chaosTargetIds.contains(target.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TargetResponse findById(Long id) {
        MonitorTarget target = getTargetEntity(id);
        boolean chaosActive = chaosRuleRepository.findByTargetId(id)
                .map(ChaosRule::hasAnyFault)
                .orElse(false);
        return toResponse(target, chaosActive);
    }

    @Transactional
    public TargetResponse create(TargetRequest request) {
        MonitorTarget target = new MonitorTarget(
                request.name().trim(),
                request.host().trim(),
                request.resolvedPort(),
                normalizePath(request.path()),
                request.protocol(),
                request.resolvedIntervalSeconds(),
                request.resolvedTimeoutMs()
        );
        boolean enabled = request.resolvedEnabled();
        target.setEnabled(enabled);
        target.setStatus(enabled ? TargetStatus.UNKNOWN : TargetStatus.PAUSED);

        MonitorTarget saved = targetRepository.save(target);
        return toResponse(saved, false);
    }

    @Transactional
    public TargetResponse update(Long id, TargetRequest request) {
        MonitorTarget target = getTargetEntity(id);
        target.setName(request.name().trim());
        target.setHost(request.host().trim());
        target.setPort(request.resolvedPort());
        target.setPath(normalizePath(request.path()));
        target.setProtocol(request.protocol());
        target.setIntervalSeconds(request.resolvedIntervalSeconds());
        target.setTimeoutMs(request.resolvedTimeoutMs());

        if (request.enabled() != null) {
            applyEnabledState(target, request.enabled());
        }

        MonitorTarget saved = targetRepository.save(target);
        boolean chaosActive = chaosRuleRepository.findByTargetId(id)
                .map(ChaosRule::hasAnyFault)
                .orElse(false);
        return toResponse(saved, chaosActive);
    }

    @Transactional
    public TargetResponse toggleEnabled(Long id) {
        MonitorTarget target = getTargetEntity(id);
        applyEnabledState(target, !target.isEnabled());
        MonitorTarget saved = targetRepository.save(target);
        boolean chaosActive = chaosRuleRepository.findByTargetId(id)
                .map(ChaosRule::hasAnyFault)
                .orElse(false);
        return toResponse(saved, chaosActive);
    }

    @Transactional
    public void delete(Long id) {
        MonitorTarget target = getTargetEntity(id);
        chaosRuleRepository.deleteByTargetId(target.getId());
        incidentRepository.deleteByTargetId(target.getId());
        probeResultRepository.deleteByTargetId(target.getId());
        targetRepository.delete(target);
        slidingWindowCalculator.clearWindow(target.getId());
    }

    @Transactional(readOnly = true)
    public List<ProbeResultResponse> findRecentProbes(Long targetId, int limit) {
        getTargetEntity(targetId);
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return probeResultRepository
                .findByTargetIdOrderByCheckedAtDesc(targetId, PageRequest.of(0, boundedLimit))
                .stream()
                .map(ProbeResultResponse::from)
                .toList();
    }

    public MonitorTarget getTargetEntity(Long id) {
        return targetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor target not found: " + id));
    }

    private void applyEnabledState(MonitorTarget target, boolean enabled) {
        target.setEnabled(enabled);
        if (!enabled) {
            target.setStatus(TargetStatus.PAUSED);
        } else if (target.getStatus() == TargetStatus.PAUSED) {
            target.setStatus(TargetStatus.UNKNOWN);
        }
    }

    private TargetResponse toResponse(MonitorTarget target, boolean chaosActive) {
        long openIncidents = incidentRepository.countByTargetIdAndStatus(target.getId(), IncidentStatus.OPEN);
        return TargetResponse.from(target, chaosActive, openIncidents);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
