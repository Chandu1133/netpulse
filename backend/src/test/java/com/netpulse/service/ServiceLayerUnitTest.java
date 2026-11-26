package com.netpulse.service;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentSeverity;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import com.netpulse.dto.IncidentResponse;
import com.netpulse.dto.TargetRequest;
import com.netpulse.dto.TracerouteResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.prober.TracerouteParser;
import com.netpulse.prober.TracerouteService;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.IncidentRepository;
import com.netpulse.repository.MonitorTargetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceLayerUnitTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private MonitorTargetRepository targetRepository;

    @Mock
    private ChaosRuleRepository chaosRuleRepository;

    @Test
    void incidentServiceSupportsAllFilterCombinationsAndNotFound() {
        MonitorTarget target = new MonitorTarget("Cloudflare", "cloudflare.com", 443, "/",
                ProtocolType.HTTPS, 10, 3000);
        target.setId(1L);

        Incident inc = new Incident();
        inc.setId(7L);
        inc.setTarget(target);
        inc.setSeverity(IncidentSeverity.CRITICAL);
        inc.setStatus(IncidentStatus.OPEN);
        inc.setTriggerType("LATENCY_SPIKE");
        inc.setSummary("Spike detected");
        inc.setOpenedAt(Instant.now());

        when(incidentRepository.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(inc));
        when(incidentRepository.findByStatusOrderByOpenedAtDesc(IncidentStatus.OPEN)).thenReturn(List.of(inc));
        when(incidentRepository.findByTargetIdOrderByOpenedAtDesc(1L)).thenReturn(List.of(inc));
        when(incidentRepository.findByTargetIdAndStatusOrderByOpenedAtDesc(1L, IncidentStatus.OPEN))
                .thenReturn(List.of(inc));
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(inc));
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        IncidentService service = new IncidentService(incidentRepository);

        assertThat(service.findIncidents(null, null)).hasSize(1);
        assertThat(service.findIncidents(IncidentStatus.OPEN, null)).hasSize(1);
        assertThat(service.findIncidents(null, 1L)).hasSize(1);
        assertThat(service.findIncidents(IncidentStatus.OPEN, 1L)).hasSize(1);

        IncidentResponse found = service.findById(7L);
        assertThat(found.id()).isEqualTo(7L);
        assertThat(found.targetName()).isEqualTo("Cloudflare");

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tracerouteServiceReturnsSimulatedPathAndIncorporatesActiveChaosLatency() {
        MonitorTarget target = new MonitorTarget("Cloudflare", "cloudflare.com", 443, "/",
                ProtocolType.HTTPS, 10, 3000);
        target.setId(2L);

        ChaosRule chaos = new ChaosRule();
        chaos.setTarget(target);
        chaos.setAddedLatencyMs(250);
        chaos.setActive(true);

        when(targetRepository.findById(2L)).thenReturn(Optional.of(target));
        when(chaosRuleRepository.findByTargetId(2L)).thenReturn(Optional.of(chaos));

        TracerouteService service = new TracerouteService(
                targetRepository, chaosRuleRepository, new TracerouteParser(), new NetPulseProperties());

        TracerouteResponse response = service.traceTarget(2L, true);

        assertThat(response.simulated()).isTrue();
        assertThat(response.host()).isEqualTo("cloudflare.com");
        assertThat(response.hops()).hasSize(5);
        assertThat(response.hops().getLast().status()).isEqualTo("SLOW");
    }

    @Test
    void targetRequestResolvesDefaultPortsByProtocol() {
        assertThat(new TargetRequest("A", "h", null, null, ProtocolType.HTTPS, null, null, null).resolvedPort()).isEqualTo(443);
        assertThat(new TargetRequest("B", "h", null, null, ProtocolType.HTTP, null, null, null).resolvedPort()).isEqualTo(80);
        assertThat(new TargetRequest("C", "h", null, null, ProtocolType.DNS, null, null, null).resolvedPort()).isEqualTo(53);
        assertThat(new TargetRequest("D", "h", null, null, ProtocolType.TCP, null, null, null).resolvedPort()).isEqualTo(443);
    }
}
