import type { Drop } from '../../api/types';
import { Badge } from '../../components/Badge';
import { ButtonLink } from '../../components/Button';
import { CartIcon } from '../../components/icons';
import { ProductImage } from '../../components/ProductImage';
import { StockMeter } from '../../components/StockMeter';
import { formatMoney, formatShortDuration } from '../../lib/format';
import { displayTitle, productTypeLabel } from './labels';

export function LiveDropCard({ drop, now }: { drop: Drop; now: number }) {
  const endsInMs = new Date(drop.endTime).getTime() - now;
  return (
    <article className="flex flex-col overflow-hidden rounded-[26px] bg-white shadow-card">
      <div className="relative h-[230px] bg-chip">
        <ProductImage src={drop.product.imageUrl} alt={drop.product.name} className="size-full" />
        <Badge tone="live" className="absolute left-3.5 top-3.5">LIVE</Badge>
        <Badge tone="dark" className="absolute right-3.5 top-3.5 px-3 py-1.5">Ends in {formatShortDuration(endsInMs)}</Badge>
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
        <StockMeter available={drop.availableStock} total={drop.totalStock} />
        <div className="mt-auto pt-1">
          {drop.soldOut ? (
            <ButtonLink to={`/drops/${drop.id}`} variant="muted" className="w-full">Sold out</ButtonLink>
          ) : (
            <ButtonLink to={`/drops/${drop.id}`} className="w-full" icon={<CartIcon size={19} />}>Buy now</ButtonLink>
          )}
        </div>
      </div>
    </article>
  );
}
