export type ProtocolType = 'HTTPS' | 'HTTP' | 'TCP' | 'DNS';
export type TargetStatus = 'UNKNOWN' | 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'PAUSED';
export type IncidentSeverity = 'WARNING' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'RESOLVED';

export interface TargetRequest {
  name: string;
  host: string;
  port?: number | null;
  path?: string | null;
  protocol: ProtocolType;
  intervalSeconds?: number;
  timeoutMs?: number;
  enabled?: boolean;
}

export interface TargetResponse {
  id: number;
  name: string;
  host: string;
  port: number;
  path: string | null;
  protocol: ProtocolType;
  intervalSeconds: number;
  timeoutMs: number;
  enabled: boolean;
  status: TargetStatus;
  lastCheckedAt: string | null;
  lastTotalLatencyMs: number | null;
  lastPacketLossPct: number | null;
  consecutiveFailures: number;
  consecutiveHealthy: number;
  chaosActive: boolean;
  openIncidentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProbeResultResponse {
  id: number;
  targetId: number;
  targetName: string;
  checkedAt: string;
  success: boolean;
  resolvedIp: string | null;
  dnsTimeMs: number | null;
  tcpConnectMs: number | null;
  tlsHandshakeMs: number | null;
  ttfbMs: number | null;
  totalLatencyMs: number | null;
  httpStatusCode: number | null;
  tlsCertDaysRemaining: number | null;
  packetLossPct: number | null;
  jitterMs: number | null;
  rollingMeanMs: number | null;
  rollingStdDevMs: number | null;
  zScore: number | null;
  chaosInjected: boolean;
  errorMessage: string | null;
}

export interface IncidentResponse {
  id: number;
  targetId: number;
  targetName: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  triggerType: string;
  summary: string;
  rootCause: string | null;
  peakLatencyMs: number | null;
  peakPacketLossPct: number | null;
  peakZScore: number | null;
  openedAt: string;
  resolvedAt: string | null;
}

export interface ChaosRuleRequest {
  addedLatencyMs: number;
  addedJitterMs: number;
  packetDropPct: number;
  forceDnsFailure: boolean;
  forceTimeout: boolean;
  active?: boolean;
}

export interface ChaosRuleResponse {
  id: number;
  targetId: number;
  targetName: string;
  addedLatencyMs: number;
  addedJitterMs: number;
  packetDropPct: number;
  forceDnsFailure: boolean;
  forceTimeout: boolean;
  active: boolean;
  updatedAt: string;
}

export interface OverviewResponse {
  totalTargets: number;
  activeTargets: number;
  healthyTargets: number;
  degradedTargets: number;
  downTargets: number;
  pausedTargets: number;
  unknownTargets: number;
  averageLatencyMs: number | null;
  openIncidents: number;
  activeChaosRules: number;
}

export interface HopDto {
  hopNumber: number;
  ip: string;
  hostname: string | null;
  rttMs: number | null;
  timedOut: boolean;
  status: 'NORMAL' | 'SLOW' | 'TIMEOUT' | string;
}

export interface TracerouteResponse {
  targetId: number;
  targetName: string;
  host: string;
  resolvedIp: string | null;
  simulated: boolean;
  osPlatform: string;
  executedAt: string;
  hops: HopDto[];
}

export interface TelemetryEvent {
  type:
    | 'CONNECTED'
    | 'PROBE_RESULT'
    | 'TARGET_UPDATED'
    | 'INCIDENT_OPENED'
    | 'INCIDENT_UPDATED'
    | 'INCIDENT_RESOLVED'
    | 'OVERVIEW_UPDATED';
  timestamp: string;
  payload: unknown;
}
