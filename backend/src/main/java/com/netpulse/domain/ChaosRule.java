package com.netpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chaos_rules")
public class ChaosRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false, unique = true)
    private MonitorTarget target;

    @Column(nullable = false)
    private int addedLatencyMs = 0;

    @Column(nullable = false)
    private int addedJitterMs = 0;

    @Column(nullable = false)
    private double packetDropPct = 0.0;

    @Column(nullable = false)
    private boolean forceDnsFailure = false;

    @Column(nullable = false)
    private boolean forceTimeout = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant updatedAt;

    public ChaosRule() {
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        this.updatedAt = Instant.now();
    }

    public boolean hasAnyFault() {
        return active && (addedLatencyMs > 0
                || addedJitterMs > 0
                || packetDropPct > 0.0
                || forceDnsFailure
                || forceTimeout);
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

    public int getAddedLatencyMs() {
        return addedLatencyMs;
    }

    public void setAddedLatencyMs(int addedLatencyMs) {
        this.addedLatencyMs = addedLatencyMs;
    }

    public int getAddedJitterMs() {
        return addedJitterMs;
    }

    public void setAddedJitterMs(int addedJitterMs) {
        this.addedJitterMs = addedJitterMs;
    }

    public double getPacketDropPct() {
        return packetDropPct;
    }

    public void setPacketDropPct(double packetDropPct) {
        this.packetDropPct = packetDropPct;
    }

    public boolean isForceDnsFailure() {
        return forceDnsFailure;
    }

    public void setForceDnsFailure(boolean forceDnsFailure) {
        this.forceDnsFailure = forceDnsFailure;
    }

    public boolean isForceTimeout() {
        return forceTimeout;
    }

    public void setForceTimeout(boolean forceTimeout) {
        this.forceTimeout = forceTimeout;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
