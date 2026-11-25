package com.netpulse.repository;

import com.netpulse.domain.ChaosRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChaosRuleRepository extends JpaRepository<ChaosRule, Long> {

    Optional<ChaosRule> findByTargetId(Long targetId);

    List<ChaosRule> findByActiveTrue();

    void deleteByTargetId(Long targetId);
}
