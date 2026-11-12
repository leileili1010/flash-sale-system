import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { endpoints } from '../api/endpoints';
import type { Drop } from '../api/types';
import { ChevronRightIcon, FlameIcon, RocketIcon } from '../components/icons';
import { LiveDropCard } from '../features/drops/DropCard';
import { HeroDrop } from '../features/drops/HeroDrop';
import { HowDropsWork } from '../features/drops/HowDropsWork';
import { UpcomingDropCard } from '../features/drops/UpcomingDropCard';
import { useServerNow } from '../lib/serverClock';

export function HomePage() {
  const now = useServerNow();
  const drops = useQuery({ queryKey: ['drops'], queryFn: () => endpoints.drops(), refetchInterval: 15_000 });

  if (drops.isPending) return <HomeSkeleton />;
  if (drops.isError) {
    return (
      <div role="alert" className="rounded-3xl bg-white p-10 text-center shadow-card">
        <h1 className="font-display text-2xl font-bold">We couldn't load the drops</h1>
        <p className="mt-2 text-muted">{drops.error.message}</p>
      </div>
    );
  }

  // Phases come from the server, but re-derive them with the synced clock so cards flip at the exact second.
  const phaseOf = (drop: Drop) => {
    if (now < new Date(drop.startTime).getTime()) return 'UPCOMING';
    return now < new Date(drop.endTime).getTime() ? 'LIVE' : 'ENDED';
  };
  const live = drops.data.filter((d) => phaseOf(d) === 'LIVE');
  const upcoming = drops.data.filter((d) => phaseOf(d) === 'UPCOMING')
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const featured = upcoming[0] ?? live[0];

  return (
    <div className="flex flex-col gap-11">
      {featured && <HeroDrop drop={featured} now={now} />}

      <section aria-labelledby="live-title" className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="live-title" className="flex items-center gap-3 font-display text-[30px] font-bold">
            <span aria-hidden="true" className="size-3 rounded-full bg-live shadow-[0_0_0_5px_var(--color-live-ring)]" />
            Live now <FlameIcon />
          </h2>
          <Link to="/" className="inline-flex items-center gap-1 text-[15px] font-bold text-pink-600 hover:text-pink-500">
            All drops <ChevronRightIcon size={16} />
          </Link>
        </div>
        {live.length === 0 ? (
          <p className="rounded-3xl bg-white p-8 text-muted shadow-card">Nothing is live right now. Check the countdown above!</p>
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[22px]">
            {live.map((drop) => <LiveDropCard key={drop.id} drop={drop} now={now} />)}
          </div>
        )}
      </section>

      {upcoming.length > 0 && (
        <section aria-labelledby="upcoming-title" className="flex flex-col gap-5">
          <h2 id="upcoming-title" className="flex items-center gap-2.5 font-display text-[30px] font-bold">
            Coming up <RocketIcon />
          </h2>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(340px,1fr))] gap-4">
            {upcoming.map((drop) => <UpcomingDropCard key={drop.id} drop={drop} now={now} />)}
          </div>
        </section>
      )}

      <HowDropsWork />
    </div>
  );
}

function HomeSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading drops" className="flex animate-pulse flex-col gap-11">
      <div className="h-[380px] rounded-[34px] bg-navy-100" />
      <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-[22px]">
        {[0, 1, 2].map((i) => <div key={i} className="h-[440px] rounded-[26px] bg-white shadow-card" />)}
      </div>
    </div>
  );
}
