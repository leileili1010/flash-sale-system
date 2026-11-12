import { countdownParts } from '../lib/format';

interface CountdownProps {
  /** Milliseconds remaining until the target moment. */
  remainingMs: number;
  /** Visual treatment: outlined boxes on the dark hero, or compact text. */
  variant?: 'hero' | 'inline';
  label: string;
}

export function Countdown({ remainingMs, variant = 'hero', label }: CountdownProps) {
  const { hours, minutes, seconds } = countdownParts(remainingMs);
  const spoken = `${label} ${Number(hours)} hours ${Number(minutes)} minutes ${Number(seconds)} seconds`;

  if (variant === 'inline') {
    return <span className="tabular font-bold" aria-label={spoken}>{hours}:{minutes}:{seconds}</span>;
  }

  const boxes: [string, string, boolean][] = [[hours, 'hours', false], [minutes, 'min', false], [seconds, 'sec', true]];
  return (
    <div role="timer" aria-label={spoken} className="flex gap-3.5 tabular">
      {boxes.map(([value, unit, accent]) => (
        <div key={unit} className="w-[72px] rounded-2xl border-[1.5px] border-white/55 bg-white/5 py-2.5 text-center">
          <div className={`font-display text-[32px] font-bold leading-tight ${accent ? 'text-[#FFA6D2]' : ''}`}>{value}</div>
          <div className="text-xs text-navy-200">{unit}</div>
        </div>
      ))}
    </div>
  );
}
