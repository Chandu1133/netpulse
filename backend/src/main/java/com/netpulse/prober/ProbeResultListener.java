package com.netpulse.prober;

import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;

/**
 * Extension hook invoked after each probe result is persisted.
 * Phase 3 registers the Anomaly Detection & WebSocket Broadcast pipeline here.
 */
public interface ProbeResultListener {

    void onProbeCompleted(MonitorTarget target, ProbeResult probeResult);
}
