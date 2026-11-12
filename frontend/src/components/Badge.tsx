import type { ReactNode } from 'react';

export type BadgeTone = 'live' | 'dark' | 'chip' | 'pink' | 'warn' | 'neutral' | 'success';

const tones: Record<BadgeTone, string> = {
  live: 'bg-white text-live-ink tracking-wide font-extrabold',
  dark: 'bg-ink text-white',
  chip: 'bg-chip text-chip-ink',
  pink: 'bg-pink-50 text-[#A02656]',
  warn: 'bg-warn-bg text-warn-ink',
  neutral: 'bg-[#EBEEF4] text-muted',
  success: 'bg-live-ring text-live-ink',
};

export function Badge({ tone = 'chip', children, className = '' }: { tone?: BadgeTone; children: ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold tabular ${tones[tone]} ${className}`}>
      {children}
    </span>
  );
}
