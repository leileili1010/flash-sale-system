import type { Drop } from '../../api/types';
import { Button, ButtonLink } from '../../components/Button';
import { Countdown } from '../../components/Countdown';
import { BellIcon, CartIcon, StarIcon } from '../../components/icons';
import { formatMoney } from '../../lib/format';
import { displayTitle } from './labels';

/** The featured drop: the next one to start, or the hottest live one. */
export function HeroDrop({ drop, now }: { drop: Drop; now: number }) {
  const upcoming = drop.phase === 'UPCOMING';
  const target = new Date(upcoming ? drop.startTime : drop.endTime).getTime();

  return (
    <section aria-labelledby="hero-title" className="relative flex flex-wrap overflow-hidden rounded-[34px] bg-hero text-white shadow-hero">
      <div className="relative z-10 flex min-w-0 flex-[1_1_440px] flex-col gap-[18px] p-10">
        <svg width="44" height="40" viewBox="0 0 44 40" aria-hidden="true" className="absolute right-8 top-8">
          <path d="M10 14 L18 4" stroke="#FFD25E" strokeWidth="3.5" strokeLinecap="round" />
          <path d="M20 22 L34 14" stroke="#FFD25E" strokeWidth="3.5" strokeLinecap="round" />
          <path d="M22 32 L38 32" stroke="#FFD25E" strokeWidth="3.5" strokeLinecap="round" />
        </svg>
        <span className="inline-flex items-center gap-2 self-start rounded-full border border-white/30 bg-white/15 px-3.5 py-1.5 text-[13px] font-bold uppercase tracking-wide">
          {upcoming ? 'Next drop' : 'Live now'} · {drop.product.series}
          <StarIcon />
        </span>
        <h1 id="hero-title" className="font-display text-[54px] font-bold leading-[1.04] tracking-tight">{displayTitle(drop.title, drop.product.series)}</h1>
        <p className="max-w-[440px] text-[17px] leading-relaxed text-navy-100">{drop.product.description}</p>
        <Countdown remainingMs={target - now} label={upcoming ? 'Starts in' : 'Ends in'} />
        <div className="flex flex-wrap gap-3.5 pt-1">
          <ButtonLink to={`/drops/${drop.id}`} variant="hero" icon={<CartIcon size={19} />}>
            View drop · {formatMoney(drop.dropPrice)}
          </ButtonLink>
          {upcoming && <Button variant="ghostLight" icon={<BellIcon />}>Remind me</Button>}
        </div>
      </div>
      <div className="relative min-h-[380px] flex-[1_1_520px]">
        <img src={drop.product.imageUrl} alt={drop.product.name} className="absolute inset-0 size-full object-cover" />
        <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-r from-navy-900 via-navy-900/40 via-20% to-transparent to-45%" />
      </div>
    </section>
  );
}
