import { useState, type FormEvent } from 'react';
import {
  Flame,
  Pause,
  Play,
  Plus,
  RefreshCw,
  Server,
  ShieldAlert,
  Trash2,
  X,
} from 'lucide-react';
import type { ProtocolType, TargetRequest, TargetResponse, TargetStatus } from '../types/telemetry';

interface TargetManagerProps {
  targets: TargetResponse[];
  selectedTargetId: number | null;
  probingTargetId: number | null;
  onSelect: (targetId: number) => void;
  onCreate: (request: TargetRequest) => Promise<void>;
  onToggle: (targetId: number) => Promise<void>;
  onDelete: (targetId: number) => Promise<void>;
  onProbeNow: (targetId: number) => Promise<void>;
}

const DEFAULT_PORTS: Record<ProtocolType, number> = {
  HTTPS: 443,
  HTTP: 80,
  TCP: 443,
  DNS: 53,
};

export function TargetManager({
  targets,
  selectedTargetId,
  probingTargetId,
  onSelect,
  onCreate,
  onToggle,
  onDelete,
  onProbeNow,
}: TargetManagerProps) {
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [host, setHost] = useState('');
  const [protocol, setProtocol] = useState<ProtocolType>('HTTPS');
  const [port, setPort] = useState<string>('443');
  const [path, setPath] = useState('/');
  const [intervalSeconds, setIntervalSeconds] = useState<string>('10');

  const handleProtocolChange = (next: ProtocolType) => {
    setProtocol(next);
    setPort(String(DEFAULT_PORTS[next]));
    if (next === 'TCP' || next === 'DNS') {
      setPath('');
    } else if (!path) {
      setPath('/');
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      await onCreate({
        name: name.trim(),
        host: host.trim(),
        protocol,
        port: port ? Number(port) : DEFAULT_PORTS[protocol],
        path: protocol === 'HTTP' || protocol === 'HTTPS' ? path.trim() || '/' : null,
        intervalSeconds: Number(intervalSeconds) || 10,
        timeoutMs: 3000,
        enabled: true,
      });
      setName('');
      setHost('');
      setProtocol('HTTPS');
      setPort('443');
      setPath('/');
      setIntervalSeconds('10');
      setShowForm(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to add target');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="rounded-2xl bg-slate-900/70 border border-slate-800/90 overflow-hidden flex flex-col">
      <div className="px-4 py-3 border-b border-slate-800/80 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Server className="h-4 w-4 text-cyan-400" />
          <h2 className="text-sm font-semibold text-white">Monitored Targets</h2>
          <span className="text-xs font-mono text-slate-400">({targets.length})</span>
        </div>

        <button
          type="button"
          onClick={() => setShowForm((prev) => !prev)}
          className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-cyan-500/15 hover:bg-cyan-500/25 text-cyan-300 border border-cyan-500/30 transition"
        >
          {showForm ? <X className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
          {showForm ? 'Cancel' : 'Add Target'}
        </button>
      </div>

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="p-4 bg-slate-950/80 border-b border-slate-800 space-y-3"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">Name</label>
              <input
                type="text"
                required
                placeholder="e.g. Stripe API"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">
                Host / IP
              </label>
              <input
                type="text"
                required
                placeholder="e.g. api.stripe.com"
                value={host}
                onChange={(e) => setHost(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs font-mono text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2.5">
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">Protocol</label>
              <select
                value={protocol}
                onChange={(e) => handleProtocolChange(e.target.value as ProtocolType)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-cyan-500"
              >
                <option value="HTTPS">HTTPS</option>
                <option value="HTTP">HTTP</option>
                <option value="TCP">TCP</option>
                <option value="DNS">DNS</option>
              </select>
            </div>
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">Port</label>
              <input
                type="number"
                min={1}
                max={65535}
                value={port}
                onChange={(e) => setPort(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-cyan-500"
              />
            </div>
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">
                Interval (s)
              </label>
              <input
                type="number"
                min={5}
                max={3600}
                value={intervalSeconds}
                onChange={(e) => setIntervalSeconds(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-cyan-500"
              />
            </div>
          </div>

          {(protocol === 'HTTP' || protocol === 'HTTPS') && (
            <div>
              <label className="block text-[11px] font-medium text-slate-400 mb-1">HTTP Path</label>
              <input
                type="text"
                placeholder="/"
                value={path}
                onChange={(e) => setPath(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-2.5 py-1.5 text-xs font-mono text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>
          )}

          {formError && (
            <div className="text-xs text-rose-400 bg-rose-500/10 border border-rose-500/30 rounded-lg px-2.5 py-1.5">
              {formError}
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={submitting}
              className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-cyan-500 hover:bg-cyan-400 text-slate-950 disabled:opacity-50 transition"
            >
              {submitting ? 'Creating...' : 'Create Monitor'}
            </button>
          </div>
        </form>
      )}

      <div className="divide-y divide-slate-800/70 overflow-y-auto max-h-[620px]">
        {targets.map((target) => {
          const isSelected = target.id === selectedTargetId;
          const isProbing = target.id === probingTargetId;
          return (
            <div
              key={target.id}
              onClick={() => onSelect(target.id)}
              className={`px-4 py-3 cursor-pointer transition border-l-2 ${
                isSelected
                  ? 'bg-cyan-500/10 border-l-cyan-400'
                  : 'hover:bg-slate-800/40 border-l-transparent'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-semibold text-white truncate">{target.name}</span>
                    <StatusBadge status={target.status} />
                    {target.chaosActive && (
                      <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                        <Flame className="h-2.5 w-2.5" />
                        CHAOS
                      </span>
                    )}
                    {target.openIncidentCount > 0 && (
                      <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold bg-rose-500/20 text-rose-300 border border-rose-500/40">
                        <ShieldAlert className="h-2.5 w-2.5" />
                        {target.openIncidentCount}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-1 text-[11px] font-mono text-slate-400">
                    <span className="px-1.5 py-0.2 rounded bg-slate-800 text-slate-300 text-[10px]">
                      {target.protocol}
                    </span>
                    <span className="truncate">
                      {target.host}:{target.port}
                      {target.path ?? ''}
                    </span>
                    <span className="text-slate-500">({target.intervalSeconds}s)</span>
                  </div>
                </div>

                <div
                  className="flex items-center gap-1 shrink-0"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    type="button"
                    title="Probe now"
                    disabled={isProbing}
                    onClick={() => onProbeNow(target.id)}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-cyan-300 hover:bg-slate-800 transition disabled:opacity-40"
                  >
                    <RefreshCw className={`h-3.5 w-3.5 ${isProbing ? 'animate-spin text-cyan-400' : ''}`} />
                  </button>
                  <button
                    type="button"
                    title={target.enabled ? 'Pause target' : 'Resume target'}
                    onClick={() => onToggle(target.id)}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-amber-300 hover:bg-slate-800 transition"
                  >
                    {target.enabled ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                  </button>
                  <button
                    type="button"
                    title="Delete target"
                    onClick={() => onDelete(target.id)}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              <div className="mt-2 flex items-center justify-between text-xs font-mono">
                <div className="flex items-center gap-4">
                  <span className="text-slate-400">
                    RTT:{' '}
                    <span className="text-slate-100 font-semibold">
                      {target.lastTotalLatencyMs != null
                        ? `${target.lastTotalLatencyMs.toFixed(1)}ms`
                        : '—'}
                    </span>
                  </span>
                  <span className="text-slate-400">
                    Loss:{' '}
                    <span
                      className={`font-semibold ${
                        (target.lastPacketLossPct ?? 0) >= 50
                          ? 'text-rose-400'
                          : (target.lastPacketLossPct ?? 0) >= 20
                          ? 'text-amber-400'
                          : 'text-emerald-400'
                      }`}
                    >
                      {target.lastPacketLossPct != null
                        ? `${target.lastPacketLossPct.toFixed(0)}%`
                        : '—'}
                    </span>
                  </span>
                </div>
                <span className="text-[10px] text-slate-500">
                  {target.lastCheckedAt
                    ? new Date(target.lastCheckedAt).toLocaleTimeString()
                    : 'Awaiting probe'}
                </span>
              </div>
            </div>
          );
        })}

        {targets.length === 0 && (
          <div className="p-8 text-center text-xs text-slate-500">
            No monitoring targets configured. Click &ldquo;Add Target&rdquo; above to start probing.
          </div>
        )}
      </div>
    </section>
  );
}

function StatusBadge({ status }: { status: TargetStatus }) {
  const styles: Record<TargetStatus, string> = {
    HEALTHY: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    DEGRADED: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    DOWN: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    PAUSED: 'bg-slate-700/40 text-slate-400 border-slate-600/40',
    UNKNOWN: 'bg-sky-500/15 text-sky-300 border-sky-500/30',
  };

  return (
    <span
      className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold border ${styles[status]}`}
    >
      {status}
    </span>
  );
}
