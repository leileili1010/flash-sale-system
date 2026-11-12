import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { endpoints } from '../api/endpoints';
import type { Drop } from '../api/types';
import { Badge } from '../components/Badge';
import { Countdown } from '../components/Countdown';
import { ClockIcon, UserIcon } from '../components/icons';
import { ProductImage } from '../components/ProductImage';
import { StockMeter } from '../components/StockMeter';
import { phaseAt } from '../features/drops/DropCard';
import { displayTitle, productTypeLabel } from '../features/drops/labels';
import { BuyButton } from '../features/purchase/BuyButton';
import { formatMoney } from '../lib/format';
import { useServerNow } from '../lib/serverClock';
import { NotFoundPage } from './NotFoundPage';

export function DropDetailPage() {
  const dropId = Number(useParams().dropId);
  const now = useServerNow();
  const drop = useQuery({
    queryKey: ['drop', dropId],
    queryFn: () => endpoints.drop(dropId),
    enabled: Number.isInteger(dropId),
    // Stock moves fast during a drop; poll so the meter stays honest.
    refetchInterval: (query) => (query.state.data && phaseAt(query.state.data, Date.now()) === 'LIVE' ? 3_000 : 30_000),
  });

  if (!Number.isInteger(dropId) || (drop.isError && 'status' in drop.error && drop.error.status === 404)) {
    return <NotFoundPage />;
  }
  if (drop.isPending) {
    return <div aria-busy="true" aria-label="Loading drop" className="h-[560px] animate-pulse rounded-[32px] bg-white shadow-card" />;
  }
  if (drop.isError) {
    return <p role="alert" className="rounded-3xl bg-white p-8 shadow-card">{drop.error.message}</p>;
  }

  const d = drop.data;
  const phase = phaseAt(d, now);
  const title = displayTitle(d.title, d.product.series);
  const savings = d.originalPrice > d.dropPrice ? Math.round((1 - d.dropPrice / d.originalPrice) * 100) : 0;

  return (
    <div className="flex flex-col gap-10 pt-4">
      <nav aria-label="Breadcrumb" className="flex gap-2 text-sm text-muted">
        <Link to="/" className="font-medium text-pink-600 hover:text-pink-500">Drops</Link>
        <span aria-hidden="true">/</span>
        <span>{d.product.series}</span>
      </nav>

      <div className="flex flex-wrap items-start gap-10">
        <section aria-label="Product image" className="relative min-w-0 flex-[1_1_440px]">
          <ProductImage src={d.product.imageUrl} alt={d.product.name}
                        className="h-[480px] w-full rounded-[32px] shadow-[0_16px_36px_rgb(20_40_90/0.16)]" />
          {phase === 'LIVE' && <Badge tone="live" className="absolute left-5 top-5 px-3 py-1.5 text-[13px]">LIVE</Badge>}
          {phase === 'UPCOMING' && <Badge tone="chip" className="absolute left-5 top-5 px-3 py-1.5 text-[13px]">COMING SOON</Badge>}
          {phase === 'ENDED' && <Badge tone="neutral" className="absolute left-5 top-5 px-3 py-1.5 text-[13px]">ENDED</Badge>}
        </section>

        <section aria-labelledby="drop-title" className="flex min-w-0 flex-[1_1_420px] flex-col gap-5">
          <div>
            <p className="text-sm font-medium text-muted">{d.product.series} · {productTypeLabel(d.product.type)}</p>
            <h1 id="drop-title" className="mt-2 font-display text-[44px] font-bold leading-[1.08] tracking-tight">{title}</h1>
          </div>
          <p className="flex flex-wrap items-baseline gap-3">
            <span className="font-display text-[34px] font-bold">{formatMoney(d.dropPrice)}</span>
            {savings > 0 && (
              <>
                <span className="text-lg text-subtle line-through">{formatMoney(d.originalPrice)}</span>
                <Badge tone="pink" className="text-[13px]">Save {savings}%</Badge>
              </>
            )}
          </p>

          <div className="flex flex-col gap-4 rounded-3xl bg-white p-5 shadow-card">
            <StockMeter available={d.availableStock} total={d.totalStock} size="lg" />
            <p className="text-sm text-muted">
              {phase === 'UPCOMING' && <>Starts in <Countdown variant="inline" remainingMs={new Date(d.startTime).getTime() - now} label="Starts in" /></>}
              {phase === 'LIVE' && <>Ends in <Countdown variant="inline" remainingMs={new Date(d.endTime).getTime() - now} label="Ends in" /></>}
              {phase === 'ENDED' && 'This drop has ended.'}
            </p>
            <BuyButton drop={d} now={now} />
            <div className="flex flex-wrap gap-x-5 gap-y-2 text-[13px] text-muted">
              <span className="inline-flex items-center gap-1.5"><UserIcon size={15} />Limit 1 per customer</span>
              <span className="inline-flex items-center gap-1.5"><ClockIcon size={15} />Pay within 10 minutes of reserving</span>
            </div>
          </div>

          <div>
            <h2 className="font-display text-[22px] font-bold">About this drop</h2>
            <p className="mt-2 text-base leading-relaxed text-[#334059]">{d.product.description}</p>
          </div>

          <SeriesLineup series={d.product.series} currentProductId={d.product.id} now={now} />
        </section>
      </div>
    </div>
  );
}

interface LineupProps {
  series: string;
  currentProductId: number;
  now: number;
}

/**
 * The characters in this series. Each links to its drop: the live one if there is one, else the
 * next upcoming, else the most recent past drop. Characters without any drop are shown but not linked.
 */
function SeriesLineup({ series, currentProductId, now }: LineupProps) {
  const products = useQuery({ queryKey: ['products'], queryFn: endpoints.products, staleTime: 60_000 });
  const drops = useQuery({ queryKey: ['drops'], queryFn: () => endpoints.drops(), staleTime: 15_000 });
  const members = products.data?.filter((p) => p.series === series) ?? [];
  if (members.length < 2) return null;

  const rank = { LIVE: 0, UPCOMING: 1, ENDED: 2 } as const;
  const dropFor = (productId: number): Drop | undefined =>
    (drops.data ?? [])
      .filter((d) => d.product.id === productId)
      .sort((a, b) => {
        const byPhase = rank[phaseAt(a, now)] - rank[phaseAt(b, now)];
        if (byPhase !== 0) return byPhase;
        return phaseAt(a, now) === 'ENDED' ? b.endTime.localeCompare(a.endTime) : a.startTime.localeCompare(b.startTime);
      })[0];

  return (
    <div>
      <h2 className="font-display text-[22px] font-bold">In this series</h2>
      <ul className="mt-3 grid grid-cols-[repeat(auto-fill,minmax(110px,1fr))] gap-2.5">
        {members.map((p) => {
          const current = p.id === currentProductId;
          const drop = dropFor(p.id);
          const phase = drop ? phaseAt(drop, now) : null;
          const caption = current ? 'Viewing' : phase === 'LIVE' ? 'Live now' : phase === 'UPCOMING' ? 'Coming soon' : phase === 'ENDED' ? 'Ended' : 'No drop yet';
          const tile = (
            <>
              <ProductImage src={p.imageUrl} alt="" className={`h-[86px] w-full ${drop || current ? '' : 'opacity-60'}`} />
              <p className="px-2 pt-2 text-center text-xs font-bold">{p.name}</p>
              <p className={`px-2 pb-2 text-center text-[11px] font-semibold ${phase === 'LIVE' && !current ? 'text-live-ink' : 'text-subtle'}`}>{caption}</p>
            </>
          );
          const frame = `block overflow-hidden rounded-2xl bg-white shadow-card ${current ? 'ring-3 ring-pink-500' : ''}`;
          return (
            <li key={p.id}>
              {drop && !current ? (
                <Link to={`/drops/${drop.id}`} aria-label={`${p.name}, ${caption}`}
                      className={`${frame} transition hover:-translate-y-0.5 hover:shadow-[0_14px_30px_rgb(20_40_90/0.16)]`}>
                  {tile}
                </Link>
              ) : (
                <div aria-current={current ? 'page' : undefined} className={frame}>{tile}</div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
