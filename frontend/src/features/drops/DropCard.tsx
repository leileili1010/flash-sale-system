import type { Drop, DropPhase } from '../../api/types';
import { Badge } from '../../components/Badge';
import { ButtonLink } from '../../components/Button';
import { CartIcon } from '../../components/icons';
import { ProductImage } from '../../components/ProductImage';
import { StockMeter } from '../../components/StockMeter';
import { formatMoney, formatShortDuration, formatStart } from '../../lib/format';
import { displayTitle, productTypeLabel } from './labels';

/** A drop's phase by the synced server clock, so cards flip at the exact second. */
export function phaseAt(drop: Drop, now: number): DropPhase {
  if (now < new Date(drop.startTime).getTime()) return 'UPCOMING';
  return now < new Date(drop.endTime).getTime() ? 'LIVE' : 'ENDED';
}

const ONE_HOUR = 3_600_000;

export function DropCard({ drop, now }: { drop: Drop; now: number }) {
  const phase = phaseAt(drop, now);
  const href = `/drops/${drop.id}`;
  const startsInMs = new Date(drop.startTime).getTime() - now;

  let corner: string;
  if (phase === 'LIVE') corner = `Ends in ${formatShortDuration(new Date(drop.endTime).getTime() - now)}`;
  else if (phase === 'UPCOMING') corner = startsInMs < ONE_HOUR ? `Starts in ${formatShortDuration(startsInMs)}` : formatStart(drop.startTime, now);
  else corner = 'Ended';

  return (
    <article className="flex flex-col overflow-hidden rounded-[26px] bg-white shadow-card">
      <div className="relative h-[230px] bg-chip">
        <ProductImage src={drop.product.imageUrl} alt={drop.product.name}
                      className={`size-full ${phase === 'ENDED' ? 'opacity-70 grayscale-[35%]' : ''}`} />
        {phase === 'LIVE' && <Badge tone="live" className="absolute left-3.5 top-3.5">LIVE</Badge>}
        {phase === 'UPCOMING' && <Badge tone="chip" className="absolute left-3.5 top-3.5">COMING SOON</Badge>}
        {phase === 'ENDED' && <Badge tone="neutral" className="absolute left-3.5 top-3.5">ENDED</Badge>}
        <Badge tone="dark" className="absolute right-3.5 top-3.5 px-3 py-1.5">{corner}</Badge>
      </div>
      <div className="flex flex-1 flex-col gap-3 px-5 pb-5 pt-4">
        <div>
          <p className="text-[13px] font-medium text-muted">{drop.product.series} · {productTypeLabel(drop.product.type)}</p>
          <h3 className="mt-1 font-display text-[21px] font-bold leading-tight">{displayTitle(drop.title, drop.product.series)}</h3>
        </div>
        <p className="flex items-baseline gap-2">
          <span className="font-display text-[26px] font-bold">{formatMoney(drop.dropPrice)}</span>
          {drop.originalPrice > drop.dropPrice && (
            <span className="text-[15px] text-subtle line-through">{formatMoney(drop.originalPrice)}</span>
          )}
        </p>
        {phase === 'UPCOMING' ? (
          <p className="text-sm text-muted"><strong className="text-ink">{drop.totalStock}</strong> units · one per customer</p>
        ) : (
          <StockMeter available={drop.availableStock} total={drop.totalStock} />
        )}
        <div className="mt-auto pt-1">
          {phase === 'LIVE' && !drop.soldOut && <ButtonLink to={href} className="w-full" icon={<CartIcon size={19} />}>Buy now</ButtonLink>}
          {phase === 'LIVE' && drop.soldOut && <ButtonLink to={href} variant="muted" className="w-full">Sold out</ButtonLink>}
          {phase === 'UPCOMING' && <ButtonLink to={href} variant="outline" className="w-full">View drop</ButtonLink>}
          {phase === 'ENDED' && <ButtonLink to={href} variant="muted" className="w-full">View details</ButtonLink>}
        </div>
      </div>
    </article>
  );
}
