package com.netpulse.config;

import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import com.netpulse.repository.MonitorTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final MonitorTargetRepository targetRepository;

    public DataSeeder(MonitorTargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    @Override
    public void run(String... args) {
        if (targetRepository.count() > 0) {
            return;
        }

        List<MonitorTarget> initialTargets = List.of(
                new MonitorTarget("Local Mock Target", "localhost", 8080, "/api/mock-target",
                        ProtocolType.HTTP, 10, 3000),
                new MonitorTarget("Cloudflare", "cloudflare.com", 443, "/",
                        ProtocolType.HTTPS, 10, 3000),
                new MonitorTarget("Google", "google.com", 443, "/",
                        ProtocolType.HTTPS, 10, 3000),
                new MonitorTarget("GitHub", "github.com", 443, "/",
                        ProtocolType.HTTPS, 15, 4000),
                new MonitorTarget("Cloudflare Edge TCP", "1.1.1.1", 443, null,
                        ProtocolType.TCP, 10, 3000),
                new MonitorTarget("Google Public DNS", "8.8.8.8", 53, null,
                        ProtocolType.DNS, 15, 3000)
        );

        targetRepository.saveAll(initialTargets);
        log.info("Seeded {} initial monitoring targets.", initialTargets.size());
    }
}
