import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { endpoints } from '../api/endpoints';

/**
 * Keeps the UI on the server's clock. A drop opens when the server says so, so countdowns use
 * the server time corrected for request latency rather than the visitor's (possibly wrong) clock.
 */
interface ServerClock {
  /** Milliseconds to add to Date.now() to get server time. */
  offsetMs: number;
  synced: boolean;
}

const ServerClockContext = createContext<ServerClock>({ offsetMs: 0, synced: false });

const RESYNC_INTERVAL_MS = 5 * 60_000;

export function ServerClockProvider({ children }: { children: ReactNode }) {
  const [clock, setClock] = useState<ServerClock>({ offsetMs: 0, synced: false });

  useEffect(() => {
    let cancelled = false;
    async function sync() {
      try {
        const sentAt = Date.now();
        const { epochMillis } = await endpoints.serverTime();
        const receivedAt = Date.now();
        // Assume the server stamped the response halfway through the round trip.
        const offsetMs = epochMillis - (sentAt + receivedAt) / 2;
        if (!cancelled) setClock({ offsetMs, synced: true });
      } catch {
        // Keep the previous offset (or the local clock) until the next attempt.
      }
    }
    void sync();
    const timer = window.setInterval(sync, RESYNC_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return <ServerClockContext.Provider value={clock}>{children}</ServerClockContext.Provider>;
}

export function useServerClock(): ServerClock {
  return useContext(ServerClockContext);
}

/** Current server time in epoch milliseconds, re-rendering every `intervalMs`. */
export function useServerNow(intervalMs = 1000): number {
  const { offsetMs } = useServerClock();
  const [localNow, setLocalNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setLocalNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);

  return localNow + offsetMs;
}
