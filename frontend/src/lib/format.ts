const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function formatMoney(amount: number): string {
  return money.format(amount);
}

export interface CountdownParts {
  hours: string;
  minutes: string;
  seconds: string;
  done: boolean;
}

/** Splits a duration into zero-padded hh/mm/ss. Hours are not capped at 24. */
export function countdownParts(remainingMs: number): CountdownParts {
  const total = Math.max(0, Math.ceil(remainingMs / 1000));
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    hours: pad(Math.floor(total / 3600)),
    minutes: pad(Math.floor((total % 3600) / 60)),
    seconds: pad(total % 60),
    done: total === 0,
  };
}

/** Short human duration for badges: "3h 48m", "12m", "45s". */
export function formatShortDuration(remainingMs: number): string {
  const total = Math.max(0, Math.ceil(remainingMs / 1000));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${total}s`;
}

const clock = new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
const dateTime = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });

export function formatClock(epochMs: number): string {
  return clock.format(epochMs);
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** "Today 19:00", "Tomorrow 18:00", or "Nov 14, 18:00" for a future start time. */
export function formatStart(iso: string, nowMs: number): string {
  const start = new Date(iso);
  const now = new Date(nowMs);
  const time = start.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  const dayDiff = Math.round(
    (Date.UTC(start.getFullYear(), start.getMonth(), start.getDate()) -
      Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())) / 86_400_000,
  );
  if (dayDiff === 0) return `Today ${time}`;
  if (dayDiff === 1) return `Tomorrow ${time}`;
  return formatDateTime(iso);
}
