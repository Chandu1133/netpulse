import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from './api/client';
import { useTelemetrySocket } from './hooks/useTelemetrySocket';
import { OverviewBar } from './components/OverviewBar';
import { TargetManager } from './components/TargetManager';
import { TelemetryPanel } from './components/TelemetryPanel';
import { HopPathView } from './components/HopPathView';
import { ChaosControlBar } from './components/ChaosControlBar';
import { IncidentLog } from './components/IncidentLog';
import type {
  ChaosRuleRequest,
  ChaosRuleResponse,
  IncidentResponse,
  OverviewResponse,
  ProbeResultResponse,
  TargetRequest,
  TargetResponse,
  TelemetryEvent,
  TracerouteResponse,
} from './types/telemetry';

const MAX_PROBES_BUFFER = 40;

export default function App() {
  const [overview, setOverview] = useState<OverviewResponse | null>(null);
  const [targets, setTargets] = useState<TargetResponse[]>([]);
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(null);
  const [probesByTarget, setProbesByTarget] = useState<Record<number, ProbeResultResponse[]>>({});
  const [incidents, setIncidents] = useState<IncidentResponse[]>([]);
  const [chaosRules, setChaosRules] = useState<Record<number, ChaosRuleResponse>>({});
  const [tracerouteByTarget, setTracerouteByTarget] = useState<Record<number, TracerouteResponse>>({});

  const [loadingProbes, setLoadingProbes] = useState(false);
  const [probingTargetId, setProbingTargetId] = useState<number | null>(null);
  const [tracingTarget, setTracingTarget] = useState(false);
  const [chaosBusy, setChaosBusy] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);

  const selectedIdRef = useRef<number | null>(null);
  selectedIdRef.current = selectedTargetId;

  const hydrateAll = useCallback(async () => {
    try {
      const [overviewRes, targetsRes, incidentsRes, chaosRes] = await Promise.all([
        api.getOverview(),
        api.getTargets(),
        api.getIncidents(),
        api.getActiveChaosRules(),
      ]);

      setOverview(overviewRes);
      setTargets(targetsRes);
      setIncidents(incidentsRes);

      const chaosMap: Record<number, ChaosRuleResponse> = {};
      for (const rule of chaosRes) {
        if (rule.active) {
          chaosMap[rule.targetId] = rule;
        }
      }
      setChaosRules(chaosMap);

      setSelectedTargetId((prev) => {
        if (prev != null && targetsRes.some((t) => t.id === prev)) {
          return prev;
        }
        return targetsRes[0]?.id ?? null;
      });
      setBannerError(null);
    } catch (err) {
      setBannerError(
        err instanceof Error
          ? `Backend unreachable: ${err.message}. Start Spring Boot on port 8080.`
          : 'Failed to connect to NetPulse backend.'
      );
    }
  }, []);

  useEffect(() => {
    void hydrateAll();
  }, [hydrateAll]);

  useEffect(() => {
    if (selectedTargetId == null) return;
    let cancelled = false;
    setLoadingProbes(true);

    api
      .getTargetProbes(selectedTargetId, MAX_PROBES_BUFFER)
      .then((list) => {
        if (!cancelled) {
          setProbesByTarget((prev) => ({ ...prev, [selectedTargetId]: list }));
        }
      })
      .catch(() => {
        // ignore transient error
      })
      .finally(() => {
        if (!cancelled) setLoadingProbes(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTargetId]);

  const handleWsEvent = useCallback((event: TelemetryEvent) => {
    switch (event.type) {
      case 'PROBE_RESULT': {
        const probe = event.payload as ProbeResultResponse;
        setProbesByTarget((prev) => {
          const existing = prev[probe.targetId] ?? [];
          if (existing.some((p) => p.id === probe.id)) {
            return prev;
          }
          return {
            ...prev,
            [probe.targetId]: [probe, ...existing].slice(0, MAX_PROBES_BUFFER),
          };
        });
        break;
      }
      case 'TARGET_UPDATED': {
        const updated = event.payload as TargetResponse;
        setTargets((prev) => {
          const idx = prev.findIndex((t) => t.id === updated.id);
          if (idx === -1) return [...prev, updated];
          const copy = [...prev];
          copy[idx] = updated;
          return copy;
        });
        break;
      }
      case 'INCIDENT_OPENED':
      case 'INCIDENT_UPDATED':
      case 'INCIDENT_RESOLVED': {
        const inc = event.payload as IncidentResponse;
        setIncidents((prev) => {
          const filtered = prev.filter((item) => item.id !== inc.id);
          return [inc, ...filtered].sort(
            (a, b) => new Date(b.openedAt).getTime() - new Date(a.openedAt).getTime()
          );
        });
        break;
      }
      case 'OVERVIEW_UPDATED': {
        setOverview(event.payload as OverviewResponse);
        break;
      }
      default:
        break;
    }
  }, []);

  const socketStatus = useTelemetrySocket(handleWsEvent);

  const selectedTarget = useMemo(
    () => targets.find((t) => t.id === selectedTargetId) ?? null,
    [targets, selectedTargetId]
  );

  const selectedProbes = selectedTargetId != null ? probesByTarget[selectedTargetId] ?? [] : [];
  const selectedChaos = selectedTargetId != null ? chaosRules[selectedTargetId] ?? null : null;
  const selectedTrace = selectedTargetId != null ? tracerouteByTarget[selectedTargetId] ?? null : null;

  const handleCreateTarget = async (request: TargetRequest) => {
    const created = await api.createTarget(request);
    setTargets((prev) => [...prev, created]);
    setSelectedTargetId(created.id);
    const nextOverview = await api.getOverview();
    setOverview(nextOverview);
  };

  const handleToggleTarget = async (id: number) => {
    const updated = await api.toggleTarget(id);
    setTargets((prev) => prev.map((t) => (t.id === id ? updated : t)));
    setOverview(await api.getOverview());
  };

  const handleDeleteTarget = async (id: number) => {
    await api.deleteTarget(id);
    setTargets((prev) => {
      const next = prev.filter((t) => t.id !== id);
      if (selectedIdRef.current === id) {
        setSelectedTargetId(next[0]?.id ?? null);
      }
      return next;
    });
    setChaosRules((prev) => {
      const copy = { ...prev };
      delete copy[id];
      return copy;
    });
    setIncidents((prev) => prev.filter((i) => i.targetId !== id));
    setOverview(await api.getOverview());
  };

  const handleProbeNow = async (id: number) => {
    setProbingTargetId(id);
    try {
      const probe = await api.triggerProbe(id);
      setProbesByTarget((prev) => {
        const existing = prev[id] ?? [];
        if (existing.some((p) => p.id === probe.id)) return prev;
        return { ...prev, [id]: [probe, ...existing].slice(0, MAX_PROBES_BUFFER) };
      });
    } finally {
      setProbingTargetId(null);
    }
  };

  const handleRunTrace = async (simulate: boolean) => {
    if (selectedTargetId == null) return;
    setTracingTarget(true);
    try {
      const trace = await api.getTraceroute(selectedTargetId, simulate);
      setTracerouteByTarget((prev) => ({ ...prev, [selectedTargetId]: trace }));
    } finally {
      setTracingTarget(false);
    }
  };

  const handleInjectChaos = async (ruleReq: ChaosRuleRequest) => {
    if (selectedTargetId == null) return;
    setChaosBusy(true);
    try {
      const saved = await api.upsertChaosRule(selectedTargetId, ruleReq);
      setChaosRules((prev) => ({ ...prev, [selectedTargetId]: saved }));
      setTargets((prev) =>
        prev.map((t) => (t.id === selectedTargetId ? { ...t, chaosActive: true } : t))
      );
      // Trigger immediate probe so the user sees the spike/incident immediately
      await api.triggerProbe(selectedTargetId);
      setOverview(await api.getOverview());
    } finally {
      setChaosBusy(false);
    }
  };

  const handleClearChaos = async () => {
    if (selectedTargetId == null) return;
    setChaosBusy(true);
    try {
      await api.clearChaosRule(selectedTargetId);
      setChaosRules((prev) => {
        const copy = { ...prev };
        delete copy[selectedTargetId];
        return copy;
      });
      setTargets((prev) =>
        prev.map((t) => (t.id === selectedTargetId ? { ...t, chaosActive: false } : t))
      );
      await api.triggerProbe(selectedTargetId);
      setOverview(await api.getOverview());
    } finally {
      setChaosBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <OverviewBar overview={overview} socketStatus={socketStatus} />

      <main className="max-w-[1600px] w-full mx-auto px-4 sm:px-6 py-5 flex-1 space-y-4">
        {bannerError && (
          <div className="rounded-xl bg-rose-950/50 border border-rose-500/40 px-4 py-2.5 text-xs text-rose-200 flex items-center justify-between gap-3">
            <span>{bannerError}</span>
            <button
              type="button"
              onClick={() => void hydrateAll()}
              className="px-2.5 py-1 rounded bg-rose-500/20 hover:bg-rose-500/30 text-rose-100 font-medium"
            >
              Retry
            </button>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-start">
          {/* Left Column: Target Table & Manager */}
          <div className="lg:col-span-4 xl:col-span-4">
            <TargetManager
              targets={targets}
              selectedTargetId={selectedTargetId}
              probingTargetId={probingTargetId}
              onSelect={setSelectedTargetId}
              onCreate={handleCreateTarget}
              onToggle={handleToggleTarget}
              onDelete={handleDeleteTarget}
              onProbeNow={handleProbeNow}
            />
          </div>

          {/* Right Column: Live Telemetry, Chaos Bar, Hop Path, Incident Log */}
          <div className="lg:col-span-8 xl:col-span-8 space-y-4">
            <ChaosControlBar
              target={selectedTarget}
              activeRule={selectedChaos}
              busy={chaosBusy}
              onInject={handleInjectChaos}
              onClear={handleClearChaos}
            />

            <TelemetryPanel
              target={selectedTarget}
              probes={selectedProbes}
              loading={loadingProbes}
            />

            <HopPathView
              target={selectedTarget}
              traceroute={selectedTrace}
              loading={tracingTarget}
              onRunTrace={handleRunTrace}
            />

            <IncidentLog incidents={incidents} onSelectTarget={setSelectedTargetId} />
          </div>
        </div>
      </main>
    </div>
  );
}
