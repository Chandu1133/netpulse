package com.netpulse.prober;

import com.netpulse.domain.MonitorTarget;

public interface NetworkProbeClient {

    ProbeExecutionResult executeProbe(MonitorTarget target);
}
