package com.netpulse.service;

import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.dto.IncidentResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> findIncidents(IncidentStatus status, Long targetId) {
        List<Incident> incidents;
        if (targetId != null && status != null) {
            incidents = incidentRepository.findByTargetIdAndStatusOrderByOpenedAtDesc(targetId, status);
        } else if (targetId != null) {
            incidents = incidentRepository.findByTargetIdOrderByOpenedAtDesc(targetId);
        } else if (status != null) {
            incidents = incidentRepository.findByStatusOrderByOpenedAtDesc(status);
        } else {
            incidents = incidentRepository.findAllByOrderByOpenedAtDesc();
        }
        return incidents.stream().map(IncidentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse findById(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
        return IncidentResponse.from(incident);
    }
}
