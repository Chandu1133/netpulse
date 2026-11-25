package com.netpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "netpulse")
public class NetPulseProperties {

    private final Prober prober = new Prober();
    private final Anomaly anomaly = new Anomaly();

    public Prober getProber() {
        return prober;
    }

    public Anomaly getAnomaly() {
        return anomaly;
    }

    public static class Prober {
        private boolean enabled = true;
        private int defaultTimeoutMs = 3000;
        private int packetProbeAttempts = 3;
        private long schedulerTickMs = 2000;
        private int tracerouteTimeoutSeconds = 15;
        private int tracerouteMaxHops = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultTimeoutMs() {
            return defaultTimeoutMs;
        }

        public void setDefaultTimeoutMs(int defaultTimeoutMs) {
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        public int getPacketProbeAttempts() {
            return packetProbeAttempts;
        }

        public void setPacketProbeAttempts(int packetProbeAttempts) {
            this.packetProbeAttempts = packetProbeAttempts;
        }

        public long getSchedulerTickMs() {
            return schedulerTickMs;
        }

        public void setSchedulerTickMs(long schedulerTickMs) {
            this.schedulerTickMs = schedulerTickMs;
        }

        public int getTracerouteTimeoutSeconds() {
            return tracerouteTimeoutSeconds;
        }

        public void setTracerouteTimeoutSeconds(int tracerouteTimeoutSeconds) {
            this.tracerouteTimeoutSeconds = tracerouteTimeoutSeconds;
        }

        public int getTracerouteMaxHops() {
            return tracerouteMaxHops;
        }

        public void setTracerouteMaxHops(int tracerouteMaxHops) {
            this.tracerouteMaxHops = tracerouteMaxHops;
        }
    }

    public static class Anomaly {
        private int windowSize = 20;
        private double warningZThreshold = 2.0;
        private double criticalZThreshold = 3.0;
        private double warningPacketLossPct = 20.0;
        private double criticalPacketLossPct = 50.0;
        private int tlsExpiryWarningDays = 14;
        private int criticalConsecutiveFailures = 2;
        private int autoResolveHealthyCycles = 3;

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }

        public double getWarningZThreshold() {
            return warningZThreshold;
        }

        public void setWarningZThreshold(double warningZThreshold) {
            this.warningZThreshold = warningZThreshold;
        }

        public double getCriticalZThreshold() {
            return criticalZThreshold;
        }

        public void setCriticalZThreshold(double criticalZThreshold) {
            this.criticalZThreshold = criticalZThreshold;
        }

        public double getWarningPacketLossPct() {
            return warningPacketLossPct;
        }

        public void setWarningPacketLossPct(double warningPacketLossPct) {
            this.warningPacketLossPct = warningPacketLossPct;
        }

        public double getCriticalPacketLossPct() {
            return criticalPacketLossPct;
        }

        public void setCriticalPacketLossPct(double criticalPacketLossPct) {
            this.criticalPacketLossPct = criticalPacketLossPct;
        }

        public int getTlsExpiryWarningDays() {
            return tlsExpiryWarningDays;
        }

        public void setTlsExpiryWarningDays(int tlsExpiryWarningDays) {
            this.tlsExpiryWarningDays = tlsExpiryWarningDays;
        }

        public int getCriticalConsecutiveFailures() {
            return criticalConsecutiveFailures;
        }

        public void setCriticalConsecutiveFailures(int criticalConsecutiveFailures) {
            this.criticalConsecutiveFailures = criticalConsecutiveFailures;
        }

        public int getAutoResolveHealthyCycles() {
            return autoResolveHealthyCycles;
        }

        public void setAutoResolveHealthyCycles(int autoResolveHealthyCycles) {
            this.autoResolveHealthyCycles = autoResolveHealthyCycles;
        }
    }
}
