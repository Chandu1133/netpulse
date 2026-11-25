package com.netpulse;

import com.netpulse.repository.MonitorTargetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NetPulseApplicationTests {

    @Autowired
    private MonitorTargetRepository targetRepository;

    @Test
    void contextLoadsAndSeedsSixInitialTargets() {
        assertThat(targetRepository.count()).isEqualTo(6);
    }
}
