package com.netpulse.repository;

import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.TargetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitorTargetRepository extends JpaRepository<MonitorTarget, Long> {

    List<MonitorTarget> findByEnabledTrueOrderByIdAsc();

    List<MonitorTarget> findAllByOrderByIdAsc();

    long countByStatus(TargetStatus status);

    long countByEnabledTrue();
}
