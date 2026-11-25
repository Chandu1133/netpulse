import { useState } from 'react';
import { AlertTriangle, CheckCircle2, ShieldAlert } from 'lucide-react';
import type { IncidentResponse } from '../types/telemetry';

interface IncidentLogProps {
  incidents: IncidentResponse[];
  onSelectTarget: (targetId: number) => void;
}

type FilterMode = 'ALL' | 'OPEN' | 'RESOLVED';

export function IncidentLog({ incidents, onSelectTarget }: IncidentLogProps) {
  const [filter, setFilter] = useState<FilterMode>('ALL');

  const filtered = incidents.filter((inc) => {
    if (filter === 'ALL') return true;
    return inc.status === filter;
  });

  const openCount = incidents.filter((i) => i.status === 'OPEN').length;

  return (
    <section className="rounded-2xl bg-slate-900/70 border border-slate-800/90 overflow-hidden">
      <div className="px-4 py-3 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <ShieldAlert className={`h-4 w-4 ${openCount > 0 ? 'text-rose-400' : 'text-slate-400'}`} />
          <h2 className="text-sm font-semibold text-white">Incident Log</h2>
          <span className="text-xs font-mono text-slate-400">({filtered.length})</span>
        </div>

        <div className="flex items-center rounded-lg bg-slate-950 p-0.5 border border-slate-800 text-xs">
          {(['ALL', 'OPEN', 'RESOLVED'] as FilterMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setFilter(mode)}
              className={`px-2.5 py-1 rounded-md font-medium transition ${
                filter === mode
                  ? 'bg-slate-800 text-white'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {mode}
            </button>
          ))}
        </div>
      </div>

      <div className="divide-y divide-slate-800/70 max-h-[340px] overflow-y-auto">
        {filtered.map((incident) => {
          const isOpen = incident.status === 'OPEN';
          const isCritical = incident.severity === 'CRITICAL';

          return (
            <div
              key={incident.id}
              onClick={() => onSelectTarget(incident.targetId)}
              className="px-4 py-3 hover:bg-slate-800/35 cursor-pointer transition"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  {isOpen ? (
                    isCritical ? (
                      <ShieldAlert className="h-4 w-4 text-rose-400 shrink-0" />
                    ) : (
                      <AlertTriangle className="h-4 w-4 text-amber-400 shrink-0" />
                    )
                  ) : (
                    <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                  )}

                  <span
                    className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-bold border ${
                      isCritical
                        ? 'bg-rose-500/15 text-rose-300 border-rose-500/35'
                        : 'bg-amber-500/15 text-amber-300 border-amber-500/35'
                    }`}
                  >
                    {incident.severity}
                  </span>

                  <span
                    className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold border ${
                      isOpen
                        ? 'bg-rose-950/60 text-rose-300 border-rose-500/30'
                        : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                    }`}
                  >
                    {incident.status}
                  </span>

                  <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-slate-300">
                    {incident.triggerType}
                  </span>

                  <span className="text-xs font-semibold text-white">{incident.targetName}</span>
                </div>

                <span className="text-[11px] font-mono text-slate-500">
                  Opened {new Date(incident.openedAt).toLocaleTimeString()}
                  {incident.resolvedAt
                    ? ` · Resolved ${new Date(incident.resolvedAt).toLocaleTimeString()}`
                    : ''}
                </span>
              </div>

              <p className="text-xs text-slate-200 mt-1.5">{incident.summary}</p>
              {incident.rootCause && (
                <p className="text-[11px] font-mono text-slate-400 mt-1">{incident.rootCause}</p>
              )}

              <div className="flex flex-wrap items-center gap-4 mt-2 text-[11px] font-mono text-slate-400">
                {incident.peakLatencyMs != null && (
                  <span>
                    Peak Latency:{' '}
                    <span className="text-slate-200">{incident.peakLatencyMs.toFixed(1)}ms</span>
                  </span>
                )}
                {incident.peakPacketLossPct != null && (
                  <span>
                    Peak Loss:{' '}
                    <span className="text-slate-200">{incident.peakPacketLossPct.toFixed(1)}%</span>
                  </span>
                )}
                {incident.peakZScore != null && (
                  <span>
                    Peak Z:{' '}
                    <span className="text-slate-200">{incident.peakZScore.toFixed(2)}</span>
                  </span>
                )}
              </div>
            </div>
          );
        })}

        {filtered.length === 0 && (
          <div className="py-8 px-4 text-center text-xs text-slate-500">
            No {filter === 'ALL' ? '' : filter.toLowerCase() + ' '}incidents recorded. Inject a chaos
            fault above to trigger live z-score or packet-loss alerting.
          </div>
        )}
      </div>
    </section>
  );
}
