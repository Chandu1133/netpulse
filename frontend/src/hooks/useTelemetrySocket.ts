import { useEffect, useRef, useState } from 'react';
import type { TelemetryEvent } from '../types/telemetry';

export type SocketStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED';

export function useTelemetrySocket(onEvent: (event: TelemetryEvent) => void): SocketStatus {
  const [status, setStatus] = useState<SocketStatus>('CONNECTING');
  const callbackRef = useRef(onEvent);
  callbackRef.current = onEvent;

  useEffect(() => {
    let ws: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let disposed = false;
    let attempt = 0;

    const connect = () => {
      if (disposed) return;
      setStatus('CONNECTING');

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/ws/telemetry`;

      try {
        ws = new WebSocket(wsUrl);
      } catch {
        scheduleReconnect();
        return;
      }

      ws.onopen = () => {
        attempt = 0;
        setStatus('CONNECTED');
      };

      ws.onmessage = (message) => {
        try {
          const parsed = JSON.parse(message.data) as TelemetryEvent;
          callbackRef.current(parsed);
        } catch {
          // ignore malformed messages
        }
      };

      ws.onclose = () => {
        setStatus('DISCONNECTED');
        scheduleReconnect();
      };

      ws.onerror = () => {
        ws?.close();
      };
    };

    const scheduleReconnect = () => {
      if (disposed) return;
      const delayMs = Math.min(1000 * Math.pow(1.6, attempt), 10000);
      attempt += 1;
      reconnectTimer = window.setTimeout(connect, delayMs);
    };

    connect();

    return () => {
      disposed = true;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      ws?.close();
    };
  }, []);

  return status;
}
