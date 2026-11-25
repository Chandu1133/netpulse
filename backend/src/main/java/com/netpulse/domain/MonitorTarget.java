package com.netpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monitor_targets")
public class MonitorTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(length = 255)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProtocolType protocol;

    @Column(nullable = false)
    private Integer intervalSeconds = 10;

    @Column(nullable = false)
    private Integer timeoutMs = 3000;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TargetStatus status = TargetStatus.UNKNOWN;

    private Instant lastCheckedAt;

    private Double lastTotalLatencyMs;

    private Double lastPacketLossPct;

    @Column(nullable = false)
    private int consecutiveFailures = 0;

    @Column(nullable = false)
    private int consecutiveHealthy = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public MonitorTarget() {
    }

    public MonitorTarget(String name, String host, Integer port, String path,
                         ProtocolType protocol, Integer intervalSeconds, Integer timeoutMs) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.path = path;
        this.protocol = protocol;
        this.intervalSeconds = intervalSeconds != null ? intervalSeconds : 10;
        this.timeoutMs = timeoutMs != null ? timeoutMs : 3000;
        this.enabled = true;
        this.status = TargetStatus.UNKNOWN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = this.enabled ? TargetStatus.UNKNOWN : TargetStatus.PAUSED;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Integer intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public void setStatus(TargetStatus status) {
        this.status = status;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Double getLastTotalLatencyMs() {
        return lastTotalLatencyMs;
    }

    public void setLastTotalLatencyMs(Double lastTotalLatencyMs) {
        this.lastTotalLatencyMs = lastTotalLatencyMs;
    }

    public Double getLastPacketLossPct() {
        return lastPacketLossPct;
    }

    public void setLastPacketLossPct(Double lastPacketLossPct) {
        this.lastPacketLossPct = lastPacketLossPct;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public int getConsecutiveHealthy() {
        return consecutiveHealthy;
    }

    public void setConsecutiveHealthy(int consecutiveHealthy) {
        this.consecutiveHealthy = consecutiveHealthy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
