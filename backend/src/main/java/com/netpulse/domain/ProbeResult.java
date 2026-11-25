package com.netpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "probe_results", indexes = {
        @Index(name = "idx_probe_target_checked_at", columnList = "target_id, checked_at")
})
public class ProbeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private MonitorTarget target;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 64)
    private String resolvedIp;

    private Double dnsTimeMs;

    private Double tcpConnectMs;

    private Double tlsHandshakeMs;

    private Double ttfbMs;

    private Double totalLatencyMs;

    private Integer httpStatusCode;

    private Integer tlsCertDaysRemaining;

    private Double packetLossPct;

    private Double jitterMs;

    private Double rollingMeanMs;

    private Double rollingStdDevMs;

    private Double zScore;

    @Column(nullable = false)
    private boolean chaosInjected = false;

    @Column(length = 512)
    private String errorMessage;

    public ProbeResult() {
    }

    @PrePersist
    void onCreate() {
        if (this.checkedAt == null) {
            this.checkedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MonitorTarget getTarget() {
        return target;
    }

    public void setTarget(MonitorTarget target) {
        this.target = target;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResolvedIp() {
        return resolvedIp;
    }

    public void setResolvedIp(String resolvedIp) {
        this.resolvedIp = resolvedIp;
    }

    public Double getDnsTimeMs() {
        return dnsTimeMs;
    }

    public void setDnsTimeMs(Double dnsTimeMs) {
        this.dnsTimeMs = dnsTimeMs;
    }

    public Double getTcpConnectMs() {
        return tcpConnectMs;
    }

    public void setTcpConnectMs(Double tcpConnectMs) {
        this.tcpConnectMs = tcpConnectMs;
    }

    public Double getTlsHandshakeMs() {
        return tlsHandshakeMs;
    }

    public void setTlsHandshakeMs(Double tlsHandshakeMs) {
        this.tlsHandshakeMs = tlsHandshakeMs;
    }

    public Double getTtfbMs() {
        return ttfbMs;
    }

    public void setTtfbMs(Double ttfbMs) {
        this.ttfbMs = ttfbMs;
    }

    public Double getTotalLatencyMs() {
        return totalLatencyMs;
    }

    public void setTotalLatencyMs(Double totalLatencyMs) {
        this.totalLatencyMs = totalLatencyMs;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Integer getTlsCertDaysRemaining() {
        return tlsCertDaysRemaining;
    }

    public void setTlsCertDaysRemaining(Integer tlsCertDaysRemaining) {
        this.tlsCertDaysRemaining = tlsCertDaysRemaining;
    }

    public Double getPacketLossPct() {
        return packetLossPct;
    }

    public void setPacketLossPct(Double packetLossPct) {
        this.packetLossPct = packetLossPct;
    }

    public Double getJitterMs() {
        return jitterMs;
    }

    public void setJitterMs(Double jitterMs) {
        this.jitterMs = jitterMs;
    }

    public Double getRollingMeanMs() {
        return rollingMeanMs;
    }

    public void setRollingMeanMs(Double rollingMeanMs) {
        this.rollingMeanMs = rollingMeanMs;
    }

    public Double getRollingStdDevMs() {
        return rollingStdDevMs;
    }

    public void setRollingStdDevMs(Double rollingStdDevMs) {
        this.rollingStdDevMs = rollingStdDevMs;
    }

    public Double getZScore() {
        return zScore;
    }

    public void setZScore(Double zScore) {
        this.zScore = zScore;
    }

    public boolean isChaosInjected() {
        return chaosInjected;
    }

    public void setChaosInjected(boolean chaosInjected) {
        this.chaosInjected = chaosInjected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
