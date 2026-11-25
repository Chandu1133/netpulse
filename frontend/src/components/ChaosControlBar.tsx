import { Flame, RotateCcw, Timer, WifiOff, Zap } from 'lucide-react';
import type { ChaosRuleRequest, ChaosRuleResponse, TargetResponse } from '../types/telemetry';

interface ChaosControlBarProps {
  target: TargetResponse | null;
  activeRule: ChaosRuleResponse | null;
  busy: boolean;
  onInject: (rule: ChaosRuleRequest) => Promise<void>;
  onClear: () => Promise<void>;
}

export function ChaosControlBar({
  target,
  activeRule,
  busy,
  onInject,
  onClear,
}: ChaosControlBarProps) {
  const disabled = !target || busy;

  return (
    <section className="rounded-2xl bg-gradient-to-r from-slate-900/90 via-slate-900/80 to-amber-950/20 border border-amber-500/25 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
        <div className="flex items-center gap-2">
          <div className="h-7 w-7 rounded-lg bg-amber-500/15 border border-amber-500/30 flex items-center justify-center">
            <Flame className="h-4 w-4 text-amber-400" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-white">Chaos Fault-Injection Bar</h2>
            <p className="text-[11px] text-slate-400">
              {target
                ? `Inject synthetic faults on "${target.name}" and watch z-score alerts & incidents fire live`
                : 'Select a target to inject synthetic faults'}
            </p>
          </div>
        </div>

        {activeRule && activeRule.active && (
          <div className="flex items-center gap-2 text-[11px] font-mono bg-amber-500/15 text-amber-200 border border-amber-500/35 px-2.5 py-1 rounded-lg">
            <span>ACTIVE:</span>
            {activeRule.addedLatencyMs > 0 && <span>+{activeRule.addedLatencyMs}ms</span>}
            {activeRule.addedJitterMs > 0 && <span>±{activeRule.addedJitterMs}ms jitter</span>}
            {activeRule.packetDropPct > 0 && <span>{activeRule.packetDropPct}% loss</span>}
            {activeRule.forceDnsFailure && <span>DNS NXDOMAIN</span>}
            {activeRule.forceTimeout && <span>FORCED TIMEOUT</span>}
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            onInject({
              addedLatencyMs: 300,
              addedJitterMs: 35,
              packetDropPct: 0,
              forceDnsFailure: false,
              forceTimeout: false,
              active: true,
            })
          }
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-amber-500/15 hover:bg-amber-500/25 text-amber-200 border border-amber-500/35 disabled:opacity-40 transition"
        >
          <Timer className="h-3.5 w-3.5 text-amber-400" />
          Latency Spike (+300ms)
        </button>

        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            onInject({
              addedLatencyMs: 0,
              addedJitterMs: 15,
              packetDropPct: 40,
              forceDnsFailure: false,
              forceTimeout: false,
              active: true,
            })
          }
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-orange-500/15 hover:bg-orange-500/25 text-orange-200 border border-orange-500/35 disabled:opacity-40 transition"
        >
          <Zap className="h-3.5 w-3.5 text-orange-400" />
          Packet Loss (40%)
        </button>

        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            onInject({
              addedLatencyMs: 0,
              addedJitterMs: 0,
              packetDropPct: 0,
              forceDnsFailure: true,
              forceTimeout: false,
              active: true,
            })
          }
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-rose-500/15 hover:bg-rose-500/25 text-rose-200 border border-rose-500/35 disabled:opacity-40 transition"
        >
          <WifiOff className="h-3.5 w-3.5 text-rose-400" />
          DNS Outage
        </button>

        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            onInject({
              addedLatencyMs: 0,
              addedJitterMs: 0,
              packetDropPct: 0,
              forceDnsFailure: false,
              forceTimeout: true,
              active: true,
            })
          }
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-rose-500/15 hover:bg-rose-500/25 text-rose-200 border border-rose-500/35 disabled:opacity-40 transition"
        >
          <WifiOff className="h-3.5 w-3.5 text-rose-400" />
          Timeout Outage
        </button>

        <button
          type="button"
          disabled={disabled || !activeRule}
          onClick={onClear}
          className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/35 disabled:opacity-40 transition"
        >
          <RotateCcw className="h-3.5 w-3.5" />
          Clear Chaos
        </button>
      </div>
    </section>
  );
}
