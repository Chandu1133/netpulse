import { useMemo } from 'react';
import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Activity, Clock, Globe, Lock, ShieldCheck, Zap } from 'lucide-react';
import type { ProbeResultResponse, TargetResponse } from '../types/telemetry';

interface TelemetryPanelProps {
  target: TargetResponse | null;
  probes: ProbeResultResponse[];
  loading: boolean;
}

interface LatencyPoint {
  time: string;
  totalLatency: number | null;
  mean: number | null;
  warningThreshold: number | null;
  criticalThreshold: number | null;
  zScore: number | null;
  chaosInjected: boolean;
  success: boolean;
  dns: number;
  tcp: number;
  tls: number;
  ttfb: number;
}

export function TelemetryPanel({ target, probes, loading }: TelemetryPanelProps) {
  const chronologicalProbes = useMemo(() => [...probes].reverse(), [probes]);
  const latest = probes[0] ?? null;

  const chartData: LatencyPoint[] = useMemo(
    () =>
      chronologicalProbes.map((p) => {
        const mean = p.rollingMeanMs;
        const std = p.rollingStdDevMs;
        const warning = mean != null && std != null ? Number((mean + 2 * std).toFixed(2)) : null;
        const critical = mean != null && std != null ? Number((mean + 3 * std).toFixed(2)) : null;

        return {
          time: new Date(p.checkedAt).toLocaleTimeString([], {
            hour12: false,
            minute: '2-digit',
            second: '2-digit',
          }),
          totalLatency: p.totalLatencyMs,
          mean,
          warningThreshold: warning,
          criticalThreshold: critical,
          zScore: p.zScore,
          chaosInjected: p.chaosInjected,
          success: p.success,
          dns: Number((p.dnsTimeMs ?? 0).toFixed(2)),
          tcp: Number((p.tcpConnectMs ?? 0).toFixed(2)),
          tls: Number((p.tlsHandshakeMs ?? 0).toFixed(2)),
          ttfb: Number((p.ttfbMs ?? 0).toFixed(2)),
        };
      }),
    [chronologicalProbes]
  );

  const breakdownData = useMemo(() => chartData.slice(-16), [chartData]);

  if (!target) {
    return (
      <div className="rounded-2xl bg-slate-900/70 border border-slate-800 p-10 text-center text-sm text-slate-400">
        Select a target on the left to inspect live latency telemetry, timing breakdown, and path hops.
      </div>
    );
  }

  return (
    <section className="rounded-2xl bg-slate-900/70 border border-slate-800/90 p-4 space-y-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/80 pb-3">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <Activity className="h-4 w-4 text-cyan-400" />
            <h2 className="text-base font-bold text-white">{target.name}</h2>
            <span className="text-xs font-mono text-slate-400">
              {target.protocol}://{target.host}:{target.port}
              {target.path ?? ''}
            </span>
          </div>
          <div className="flex items-center gap-3 mt-1 text-xs font-mono text-slate-400 flex-wrap">
            <span className="inline-flex items-center gap-1">
              <Globe className="h-3 w-3 text-slate-500" />
              IP: <span className="text-slate-200">{latest?.resolvedIp ?? '—'}</span>
            </span>
            {latest?.httpStatusCode != null && (
              <span>
                HTTP:{' '}
                <span
                  className={
                    latest.httpStatusCode < 400 ? 'text-emerald-400 font-semibold' : 'text-rose-400 font-semibold'
                  }
                >
                  {latest.httpStatusCode}
                </span>
              </span>
            )}
            {latest?.tlsCertDaysRemaining != null && (
              <span className="inline-flex items-center gap-1">
                <Lock className="h-3 w-3 text-slate-500" />
                TLS Cert:{' '}
                <span
                  className={
                    latest.tlsCertDaysRemaining < 14
                      ? 'text-amber-400 font-semibold'
                      : 'text-emerald-400 font-semibold'
                  }
                >
                  {latest.tlsCertDaysRemaining}d remaining
                </span>
              </span>
            )}
            {latest?.rollingMeanMs != null && latest?.rollingStdDevMs != null && (
              <span className="inline-flex items-center gap-1">
                <ShieldCheck className="h-3 w-3 text-cyan-400" />
                Baseline: μ={latest.rollingMeanMs.toFixed(1)}ms, σ={latest.rollingStdDevMs.toFixed(1)}ms
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {latest?.zScore != null && (
            <span
              className={`px-2.5 py-1 rounded-lg text-xs font-mono font-semibold border ${
                latest.zScore > 3
                  ? 'bg-rose-500/15 text-rose-300 border-rose-500/35'
                  : latest.zScore > 2
                  ? 'bg-amber-500/15 text-amber-300 border-amber-500/35'
                  : 'bg-slate-800 text-slate-200 border-slate-700'
              }`}
            >
              z-score: {latest.zScore > 0 ? `+${latest.zScore.toFixed(2)}` : latest.zScore.toFixed(2)}
            </span>
          )}
        </div>
      </div>

      {/* Latest Error Banner */}
      {latest && !latest.success && latest.errorMessage && (
        <div className="rounded-xl bg-rose-950/40 border border-rose-500/40 px-3.5 py-2 text-xs font-mono text-rose-200 flex items-center gap-2">
          <Zap className="h-4 w-4 text-rose-400 shrink-0" />
          <span>Last probe failed: {latest.errorMessage}</span>
        </div>
      )}

      {/* Per-Stage Timing KPIs */}
      <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
        <MiniStat label="DNS" value={formatMs(latest?.dnsTimeMs)} color="text-sky-300" />
        <MiniStat label="TCP Connect" value={formatMs(latest?.tcpConnectMs)} color="text-indigo-300" />
        <MiniStat label="TLS Handshake" value={formatMs(latest?.tlsHandshakeMs)} color="text-violet-300" />
        <MiniStat label="TTFB" value={formatMs(latest?.ttfbMs)} color="text-cyan-300" />
        <MiniStat
          label="Packet Loss"
          value={latest?.packetLossPct != null ? `${latest.packetLossPct.toFixed(1)}%` : '—'}
          color={
            (latest?.packetLossPct ?? 0) >= 50
              ? 'text-rose-400'
              : (latest?.packetLossPct ?? 0) >= 20
              ? 'text-amber-400'
              : 'text-emerald-400'
          }
        />
        <MiniStat label="Jitter" value={formatMs(latest?.jitterMs)} color="text-slate-200" />
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {/* Real-Time Latency + Dynamic Anomaly Band */}
        <div className="rounded-xl bg-slate-950/70 border border-slate-800/80 p-3.5">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300 flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5 text-cyan-400" />
              Total Latency &amp; Anomaly Threshold Band (ms)
            </h3>
            <span className="text-[11px] font-mono text-slate-500">
              {chartData.length} samples
            </span>
          </div>

          <div className="h-60 w-full">
            {loading && chartData.length === 0 ? (
              <EmptyChart label="Loading telemetry..." />
            ) : chartData.length === 0 ? (
              <EmptyChart label="Waiting for first probe cycle..." />
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartData} margin={{ top: 8, right: 12, left: -12, bottom: 0 }}>
                  <defs>
                    <linearGradient id="warnBandFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.18} />
                      <stop offset="95%" stopColor="#f59e0b" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 10 }} minTickGap={18} />
                  <YAxis tick={{ fill: '#64748b', fontSize: 10 }} unit="ms" />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#0f172a',
                      borderColor: '#334155',
                      borderRadius: '0.75rem',
                      fontSize: '12px',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: '11px' }} />
                  <Area
                    type="monotone"
                    dataKey="warningThreshold"
                    name="Warn Band (μ+2σ)"
                    stroke="#f59e0b"
                    strokeDasharray="4 4"
                    strokeWidth={1.5}
                    fill="url(#warnBandFill)"
                    connectNulls
                  />
                  <Line
                    type="monotone"
                    dataKey="criticalThreshold"
                    name="Critical (μ+3σ)"
                    stroke="#f43f5e"
                    strokeDasharray="3 3"
                    strokeWidth={1.5}
                    dot={false}
                    connectNulls
                  />
                  <Line
                    type="monotone"
                    dataKey="totalLatency"
                    name="Latency (ms)"
                    stroke="#22d3ee"
                    strokeWidth={2.2}
                    dot={{ r: 2.5, fill: '#22d3ee' }}
                    activeDot={{ r: 5, fill: '#67e8f9' }}
                    connectNulls
                  />
                </ComposedChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Stacked Timing Breakdown Bar */}
        <div className="rounded-xl bg-slate-950/70 border border-slate-800/80 p-3.5">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
              Latency Breakdown (DNS / TCP / TLS / TTFB)
            </h3>
            <span className="text-[11px] font-mono text-slate-500">Last {breakdownData.length}</span>
          </div>

          <div className="h-60 w-full">
            {breakdownData.length === 0 ? (
              <EmptyChart label="Waiting for probe timing breakdown..." />
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={breakdownData} margin={{ top: 8, right: 12, left: -12, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 10 }} minTickGap={14} />
                  <YAxis tick={{ fill: '#64748b', fontSize: 10 }} unit="ms" />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#0f172a',
                      borderColor: '#334155',
                      borderRadius: '0.75rem',
                      fontSize: '12px',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: '11px' }} />
                  <Bar dataKey="dns" name="DNS" stackId="timing" fill="#38bdf8" />
                  <Bar dataKey="tcp" name="TCP" stackId="timing" fill="#818cf8" />
                  <Bar dataKey="tls" name="TLS" stackId="timing" fill="#c084fc" />
                  <Bar dataKey="ttfb" name="TTFB" stackId="timing" fill="#2dd4bf" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function MiniStat({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="rounded-xl bg-slate-950/70 border border-slate-800/80 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wider text-slate-500">{label}</div>
      <div className={`text-sm font-bold font-mono mt-0.5 ${color}`}>{value}</div>
    </div>
  );
}

function EmptyChart({ label }: { label: string }) {
  return (
    <div className="h-full flex items-center justify-center text-xs font-mono text-slate-500">
      {label}
    </div>
  );
}

function formatMs(val: number | null | undefined): string {
  return val != null ? `${val.toFixed(1)} ms` : '—';
}
