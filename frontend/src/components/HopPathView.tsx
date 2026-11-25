import { ArrowRight, GitCommitHorizontal, Play, Route, Sparkles } from 'lucide-react';
import type { TargetResponse, TracerouteResponse } from '../types/telemetry';

interface HopPathViewProps {
  target: TargetResponse | null;
  traceroute: TracerouteResponse | null;
  loading: boolean;
  onRunTrace: (simulate: boolean) => Promise<void>;
}

export function HopPathView({ target, traceroute, loading, onRunTrace }: HopPathViewProps) {
  return (
    <section className="rounded-2xl bg-slate-900/70 border border-slate-800/90 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-2">
          <Route className="h-4 w-4 text-cyan-400" />
          <h2 className="text-sm font-semibold text-white">Hop-by-Hop Path Inspector</h2>
          {traceroute && (
            <span
              className={`text-[10px] font-mono uppercase px-1.5 py-0.5 rounded border ${
                traceroute.simulated
                  ? 'bg-violet-500/15 text-violet-300 border-violet-500/30'
                  : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
              }`}
            >
              {traceroute.simulated ? 'Simulated Path' : `Live (${traceroute.osPlatform})`}
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={!target || loading}
            onClick={() => onRunTrace(false)}
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 disabled:opacity-40 transition"
          >
            <Play className="h-3 w-3 text-cyan-400" />
            {loading ? 'Tracing...' : 'Live Traceroute'}
          </button>
          <button
            type="button"
            disabled={!target || loading}
            onClick={() => onRunTrace(true)}
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-violet-500/15 hover:bg-violet-500/25 text-violet-300 border border-violet-500/30 disabled:opacity-40 transition"
          >
            <Sparkles className="h-3 w-3" />
            Simulated Demo
          </button>
        </div>
      </div>

      {!traceroute ? (
        <div className="rounded-xl bg-slate-950/60 border border-dashed border-slate-800 py-6 px-4 text-center text-xs text-slate-500">
          {target
            ? `Click "Live Traceroute" or "Simulated Demo" to inspect network hops to ${target.host}.`
            : 'Select a target to trace its network path.'}
        </div>
      ) : (
        <div className="overflow-x-auto pb-2">
          <div className="flex items-center gap-2 min-w-max">
            {traceroute.hops.map((hop, idx) => {
              const style =
                hop.status === 'TIMEOUT' || hop.timedOut
                  ? 'border-rose-500/50 bg-rose-950/30 border-dashed text-rose-200'
                  : hop.status === 'SLOW'
                  ? 'border-amber-500/50 bg-amber-950/25 text-amber-200'
                  : 'border-emerald-500/35 bg-slate-950/80 text-slate-100';

              const rttColor =
                hop.status === 'TIMEOUT' || hop.timedOut
                  ? 'text-rose-400'
                  : hop.status === 'SLOW'
                  ? 'text-amber-300'
                  : 'text-emerald-400';

              return (
                <div key={`${hop.hopNumber}-${idx}`} className="flex items-center gap-2">
                  <div className={`rounded-xl border px-3 py-2 min-w-[138px] ${style}`}>
                    <div className="flex items-center justify-between text-[10px] font-mono text-slate-400 mb-1">
                      <span className="inline-flex items-center gap-1">
                        <GitCommitHorizontal className="h-3 w-3" />
                        Hop {hop.hopNumber}
                      </span>
                      <span className={`font-semibold ${rttColor}`}>
                        {hop.timedOut || hop.rttMs == null ? '* * *' : `${hop.rttMs.toFixed(1)} ms`}
                      </span>
                    </div>
                    <div className="text-xs font-mono font-semibold truncate">{hop.ip}</div>
                    <div className="text-[10px] text-slate-400 truncate mt-0.5">
                      {hop.hostname ?? (hop.timedOut ? 'ICMP Timeout' : 'Transit Router')}
                    </div>
                  </div>
                  {idx < traceroute.hops.length - 1 && (
                    <ArrowRight className="h-4 w-4 text-slate-600 shrink-0" />
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
