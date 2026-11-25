import type {
  ChaosRuleRequest,
  ChaosRuleResponse,
  IncidentResponse,
  IncidentStatus,
  OverviewResponse,
  ProbeResultResponse,
  TargetRequest,
  TargetResponse,
  TracerouteResponse,
} from '../types/telemetry';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      if (body?.message) {
        message = body.message;
      }
    } catch {
      // fallback to status text
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const api = {
  getOverview: () => request<OverviewResponse>('/api/overview'),

  getTargets: () => request<TargetResponse[]>('/api/targets'),

  createTarget: (payload: TargetRequest) =>
    request<TargetResponse>('/api/targets', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  toggleTarget: (id: number) =>
    request<TargetResponse>(`/api/targets/${id}/toggle`, {
      method: 'PATCH',
    }),

  deleteTarget: (id: number) =>
    request<void>(`/api/targets/${id}`, {
      method: 'DELETE',
    }),

  getTargetProbes: (id: number, limit = 40) =>
    request<ProbeResultResponse[]>(`/api/targets/${id}/probes?limit=${limit}`),

  triggerProbe: (id: number) =>
    request<ProbeResultResponse>(`/api/targets/${id}/probe`, {
      method: 'POST',
    }),

  getTraceroute: (id: number, simulate = false) =>
    request<TracerouteResponse>(`/api/targets/${id}/traceroute?simulate=${simulate}`),

  getIncidents: (status?: IncidentStatus, targetId?: number) => {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (targetId !== undefined) params.set('targetId', String(targetId));
    const qs = params.toString();
    return request<IncidentResponse[]>(`/api/incidents${qs ? `?${qs}` : ''}`);
  },

  getActiveChaosRules: () => request<ChaosRuleResponse[]>('/api/chaos'),

  upsertChaosRule: (targetId: number, payload: ChaosRuleRequest) =>
    request<ChaosRuleResponse>(`/api/chaos/${targetId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  clearChaosRule: (targetId: number) =>
    request<void>(`/api/chaos/${targetId}`, {
      method: 'DELETE',
    }),
};
