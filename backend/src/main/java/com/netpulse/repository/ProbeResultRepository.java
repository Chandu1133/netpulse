package com.netpulse.repository;

import com.netpulse.domain.ProbeResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProbeResultRepository extends JpaRepository<ProbeResult, Long> {

    List<ProbeResult> findByTargetIdOrderByCheckedAtDesc(Long targetId, Pageable pageable);

    List<ProbeResult> findTop20ByTargetIdAndSuccessTrueOrderByCheckedAtDesc(Long targetId);

    void deleteByTargetId(Long targetId);
}
