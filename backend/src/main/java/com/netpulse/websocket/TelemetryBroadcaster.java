package com.netpulse.websocket;

import com.netpulse.dto.IncidentResponse;
import com.netpulse.dto.OverviewResponse;
import com.netpulse.dto.ProbeResultResponse;
import com.netpulse.dto.TargetResponse;
import org.springframework.stereotype.Service;

@Service
public class TelemetryBroadcaster {

    private final TelemetryWebSocketHandler handler;

    public TelemetryBroadcaster(TelemetryWebSocketHandler handler) {
        this.handler = handler;
    }

    public void broadcastProbeResult(ProbeResultResponse probe) {
        handler.broadcast(TelemetryEvent.of("PROBE_RESULT", probe));
    }

    public void broadcastTargetUpdate(TargetResponse target) {
        handler.broadcast(TelemetryEvent.of("TARGET_UPDATED", target));
    }

    public void broadcastIncidentOpened(IncidentResponse incident) {
        handler.broadcast(TelemetryEvent.of("INCIDENT_OPENED", incident));
    }

    public void broadcastIncidentUpdated(IncidentResponse incident) {
        handler.broadcast(TelemetryEvent.of("INCIDENT_UPDATED", incident));
    }

    public void broadcastIncidentResolved(IncidentResponse incident) {
        handler.broadcast(TelemetryEvent.of("INCIDENT_RESOLVED", incident));
    }

    public void broadcastOverview(OverviewResponse overview) {
        handler.broadcast(TelemetryEvent.of("OVERVIEW_UPDATED", overview));
    }
}
