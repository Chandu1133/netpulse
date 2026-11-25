import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  ExternalLink,
  Flame,
  Gauge,
  Radio,
  ShieldAlert,
  WifiOff,
} from 'lucide-react';
import type { OverviewResponse } from '../types/telemetry';
import type { SocketStatus } from '../hooks/useTelemetrySocket';

interface OverviewBarProps {
  overview: OverviewResponse | null;
  socketStatus: SocketStatus;
}

export function OverviewBar({ overview, socketStatus }: OverviewBarProps) {
  const openIncidents = overview?.openIncidents ?? 0;
  const activeChaos = overview?.activeChaosRules ?? 0;

  return (
    <header className="border-b border-slate-800/80 bg-slate-950/90 backdrop-blur sticky top-0 z-20">
      <div className="max-w-[1600px] mx-auto px-4 sm:px-6 py-3">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <Activity className="h-5 w-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-white">NetPulse</h1>
                <span className="text-[10px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded bg-cyan-500/10 text-cyan-300 border border-cyan-500/25">
                  Java 21 Virtual Threads
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Distributed Network Telemetry, Synthetic Monitoring &amp; Z-Score Anomaly Engine
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            {activeChaos > 0 && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30 animate-pulse">
                <Flame className="h-3.5 w-3.5" />
                {activeChaos} Chaos Active
              </span>
            )}

            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${
                socketStatus === 'CONNECTED'
                  ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                  : socketStatus === 'CONNECTING'
                  ? 'bg-amber-500/10 text-amber-300 border-amber-500/30'
                  : 'bg-rose-500/10 text-rose-300 border-rose-500/30'
              }`}
            >
              <Radio
                className={`h-3.5 w-3.5 ${socketStatus === 'CONNECTED' ? 'animate-pulse' : ''}`}
              />
              {socketStatus === 'CONNECTED'
                ? 'Live WS Stream'
                : socketStatus === 'CONNECTING'
                ? 'Connecting...'
                : 'Reconnecting...'}
            </span>

            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700/80 transition"
            >
              API Docs
              <ExternalLink className="h-3 w-3" />
            </a>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
          <MetricTile
            label="Active Targets"
            value={`${overview?.activeTargets ?? 0} / ${overview?.totalTargets ?? 0}`}
            icon={<Activity className="h-4 w-4 text-cyan-400" />}
            accent="border-slate-800"
          />
          <MetricTile
            label="Healthy"
            value={String(overview?.healthyTargets ?? 0)}
            icon={<CheckCircle2 className="h-4 w-4 text-emerald-400" />}
            valueClass="text-emerald-400"
            accent="border-emerald-500/20"
          />
          <MetricTile
            label="Degraded"
            value={String(overview?.degradedTargets ?? 0)}
            icon={<AlertTriangle className="h-4 w-4 text-amber-400" />}
            valueClass="text-amber-400"
            accent="border-amber-500/20"
          />
          <MetricTile
            label="Down"
            value={String(overview?.downTargets ?? 0)}
            icon={<WifiOff className="h-4 w-4 text-rose-400" />}
            valueClass="text-rose-400"
            accent="border-rose-500/20"
          />
          <MetricTile
            label="Global Avg Latency"
            value={
              overview?.averageLatencyMs != null ? `${overview.averageLatencyMs.toFixed(1)} ms` : '—'
            }
            icon={<Gauge className="h-4 w-4 text-sky-400" />}
            valueClass="text-sky-300"
            accent="border-slate-800"
          />
          <MetricTile
            label="Open Incidents"
            value={String(openIncidents)}
            icon={
              <ShieldAlert
                className={`h-4 w-4 ${openIncidents > 0 ? 'text-rose-400 animate-pulse' : 'text-slate-500'}`}
              />
            }
            valueClass={openIncidents > 0 ? 'text-rose-400' : 'text-slate-300'}
            accent={openIncidents > 0 ? 'border-rose-500/40 bg-rose-950/20' : 'border-slate-800'}
          />
        </div>
      </div>
    </header>
  );
}

interface MetricTileProps {
  label: string;
  value: string;
  icon: React.ReactNode;
  valueClass?: string;
  accent?: string;
}

function MetricTile({
  label,
  value,
  icon,
  valueClass = 'text-white',
  accent = 'border-slate-800',
}: MetricTileProps) {
  return (
    <div className={`rounded-xl bg-slate-900/80 border ${accent} px-3.5 py-2.5`}>
      <div className="flex items-center justify-between text-[11px] text-slate-400 mb-1">
        <span>{label}</span>
        {icon}
      </div>
      <div className={`text-xl font-bold font-mono tracking-tight ${valueClass}`}>{value}</div>
    </div>
  );
}
