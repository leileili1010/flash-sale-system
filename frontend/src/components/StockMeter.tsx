interface StockMeterProps {
  available: number;
  total: number;
  size?: 'sm' | 'lg';
}

export function StockMeter({ available, total, size = 'sm' }: StockMeterProps) {
  const percent = total > 0 ? Math.min(100, Math.max(0, (available / total) * 100)) : 0;
  return (
    <div className="flex flex-col gap-1.5">
      <div
        role="meter"
        aria-label="Stock remaining"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={available}
        className={`overflow-hidden rounded-full bg-track ${size === 'lg' ? 'h-3' : 'h-[7px]'}`}
      >
        <div className="h-full rounded-full bg-gradient-to-r from-[#FF6F9C] to-[#E0407A] transition-[width] duration-500"
             style={{ width: `${percent}%` }} />
      </div>
      <p className={`tabular text-muted ${size === 'lg' ? 'text-[15px]' : 'text-sm'}`}>
        <strong className={`text-ink ${size === 'lg' ? 'text-2xl' : ''}`}>{available}</strong> of {total} left
      </p>
    </div>
  );
}
