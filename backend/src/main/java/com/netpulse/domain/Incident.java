package com.netpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_target_status", columnList = "target_id, status"),
        @Index(name = "idx_incident_opened_at", columnList = "opened_at")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private MonitorTarget target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(nullable = false, length = 64)
    private String triggerType;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(length = 1024)
    private String rootCause;

    private Double peakLatencyMs;

    private Double peakPacketLossPct;

    private Double peakZScore;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    private Instant resolvedAt;

    public Incident() {
    }

    @PrePersist
    void onCreate() {
        if (this.openedAt == null) {
            this.openedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = IncidentStatus.OPEN;
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

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public Double getPeakLatencyMs() {
        return peakLatencyMs;
    }

    public void setPeakLatencyMs(Double peakLatencyMs) {
        this.peakLatencyMs = peakLatencyMs;
    }

    public Double getPeakPacketLossPct() {
        return peakPacketLossPct;
    }

    public void setPeakPacketLossPct(Double peakPacketLossPct) {
        this.peakPacketLossPct = peakPacketLossPct;
    }

    public Double getPeakZScore() {
        return peakZScore;
    }

    public void setPeakZScore(Double peakZScore) {
        this.peakZScore = peakZScore;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
