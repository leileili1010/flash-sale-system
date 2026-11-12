import { Link } from 'react-router';
import type { Drop } from '../../api/types';
import { Badge } from '../../components/Badge';
import { BellIcon } from '../../components/icons';
import { ProductImage } from '../../components/ProductImage';
import { formatMoney, formatShortDuration, formatStart } from '../../lib/format';
import { displayTitle } from './labels';

const ONE_HOUR = 3_600_000;

export function UpcomingDropCard({ drop, now }: { drop: Drop; now: number }) {
  const startsInMs = new Date(drop.startTime).getTime() - now;
  const when = startsInMs < ONE_HOUR ? `Starts in ${formatShortDuration(startsInMs)}` : formatStart(drop.startTime, now);

  return (
    <article className="flex items-center gap-4 rounded-[22px] bg-white py-3 pl-3 pr-4 shadow-card">
      <ProductImage src={drop.product.imageUrl} alt={drop.product.name} className="h-20 w-[88px] flex-none rounded-2xl" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <Badge tone="chip" className="self-start">{when}</Badge>
        <h3 className="font-display text-[17px] font-bold leading-tight">
          <Link to={`/drops/${drop.id}`} className="hover:text-pink-600">{displayTitle(drop.title, drop.product.series)}</Link>
        </h3>
        <p className="text-sm text-muted">{formatMoney(drop.dropPrice)} · {drop.totalStock} units</p>
      </div>
      <button
        type="button"
        aria-label={`Remind me about ${displayTitle(drop.title, drop.product.series)}`}
        className="flex size-11 flex-none items-center justify-center rounded-full border border-line bg-white text-ink hover:border-pink-400 hover:text-pink-600"
      >
        <BellIcon />
      </button>
    </article>
  );
}
