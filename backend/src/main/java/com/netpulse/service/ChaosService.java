package com.netpulse.service;

import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.dto.ChaosRuleRequest;
import com.netpulse.dto.ChaosRuleResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.MonitorTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChaosService {

    private final ChaosRuleRepository chaosRuleRepository;
    private final MonitorTargetRepository targetRepository;

    public ChaosService(ChaosRuleRepository chaosRuleRepository,
                        MonitorTargetRepository targetRepository) {
        this.chaosRuleRepository = chaosRuleRepository;
        this.targetRepository = targetRepository;
    }

    @Transactional(readOnly = true)
    public List<ChaosRuleResponse> findAllActive() {
        return chaosRuleRepository.findByActiveTrue().stream()
                .map(ChaosRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChaosRuleResponse findByTargetId(Long targetId) {
        ensureTargetExists(targetId);
        return chaosRuleRepository.findByTargetId(targetId)
                .map(ChaosRuleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No active chaos rule for target: " + targetId));
    }

    @Transactional
    public ChaosRuleResponse upsertRule(Long targetId, ChaosRuleRequest request) {
        MonitorTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor target not found: " + targetId));

        ChaosRule rule = chaosRuleRepository.findByTargetId(targetId).orElseGet(() -> {
            ChaosRule created = new ChaosRule();
            created.setTarget(target);
            return created;
        });

        rule.setAddedLatencyMs(request.addedLatencyMs());
        rule.setAddedJitterMs(request.addedJitterMs());
        rule.setPacketDropPct(request.packetDropPct());
        rule.setForceDnsFailure(request.forceDnsFailure());
        rule.setForceTimeout(request.forceTimeout());
        rule.setActive(request.resolvedActive());

        ChaosRule saved = chaosRuleRepository.save(rule);
        return ChaosRuleResponse.from(saved);
    }

    @Transactional
    public void clearRule(Long targetId) {
        ensureTargetExists(targetId);
        chaosRuleRepository.deleteByTargetId(targetId);
    }

    private void ensureTargetExists(Long targetId) {
        if (!targetRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("Monitor target not found: " + targetId);
        }
    }
}
