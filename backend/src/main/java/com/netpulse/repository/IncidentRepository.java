package com.netpulse.repository;

import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByOrderByOpenedAtDesc();

    List<Incident> findByStatusOrderByOpenedAtDesc(IncidentStatus status);

    List<Incident> findByTargetIdOrderByOpenedAtDesc(Long targetId);

    List<Incident> findByTargetIdAndStatusOrderByOpenedAtDesc(Long targetId, IncidentStatus status);

    Optional<Incident> findFirstByTargetIdAndStatusOrderByOpenedAtDesc(Long targetId, IncidentStatus status);

    long countByStatus(IncidentStatus status);

    long countByTargetIdAndStatus(Long targetId, IncidentStatus status);

    void deleteByTargetId(Long targetId);
}
