import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { endpoints } from '../api/endpoints';
import type { DropPhase } from '../api/types';
import { DropCard, phaseAt } from '../features/drops/DropCard';
import { useServerNow } from '../lib/serverClock';

const tabs: { phase: DropPhase; label: string; empty: string }[] = [
  { phase: 'LIVE', label: 'Live now', empty: 'Nothing is live right now.' },
  { phase: 'UPCOMING', label: 'Coming up', empty: 'No upcoming drops are scheduled yet.' },
  { phase: 'ENDED', label: 'Ended', empty: 'No drops have ended yet.' },
];

/** Every published drop, grouped by phase. The selected tab lives in the URL so it can be shared. */
export function AllDropsPage() {
  const now = useServerNow();
  const [params, setParams] = useSearchParams();
  const selected = tabs.find((t) => t.phase.toLowerCase() === params.get('phase'))?.phase ?? 'LIVE';
  const drops = useQuery({ queryKey: ['drops'], queryFn: () => endpoints.drops(), refetchInterval: 15_000 });

  const byPhase = (phase: DropPhase) => {
    const list = (drops.data ?? []).filter((d) => phaseAt(d, now) === phase);
    if (phase === 'UPCOMING') return list.sort((a, b) => a.startTime.localeCompare(b.startTime));
    if (phase === 'ENDED') return list.sort((a, b) => b.endTime.localeCompare(a.endTime));
    return list.sort((a, b) => a.endTime.localeCompare(b.endTime));
  };
  const visible = byPhase(selected);
  const tab = tabs.find((t) => t.phase === selected)!;

  return (
    <div className="flex flex-col gap-7 pt-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[40px] font-bold leading-tight">All drops</h1>
          <p className="mt-1 text-muted">Every limited release, live, upcoming and past.</p>
        </div>
        <div role="tablist" aria-label="Drop status" className="flex rounded-full bg-white p-1 shadow-card">
          {tabs.map((t) => (
            <button key={t.phase} type="button" role="tab" aria-selected={selected === t.phase}
                    onClick={() => setParams(t.phase === 'LIVE' ? {} : { phase: t.phase.toLowerCase() }, { replace: true })}
                    className={`rounded-full px-4 py-2 text-sm font-bold transition ${selected === t.phase ? 'bg-ink text-white' : 'text-muted hover:text-ink'}`}>
              {t.label}
              {drops.data && <span className="ml-1.5 tabular opacity-70">{byPhase(t.phase).length}</span>}
            </button>
          ))}
        </div>
      </div>

      {drops.isPending && (
        <div aria-busy="true" aria-label="Loading drops" className="grid animate-pulse grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[22px]">
          {[0, 1, 2].map((i) => <div key={i} className="h-[440px] rounded-[26px] bg-white shadow-card" />)}
        </div>
      )}
      {drops.isError && <p role="alert" className="rounded-3xl bg-white p-8 shadow-card">{drops.error.message}</p>}
      {drops.isSuccess && (
        <section role="tabpanel" aria-label={tab.label}>
          {visible.length === 0 ? (
            <p className="rounded-3xl bg-white p-10 text-center text-muted shadow-card">{tab.empty}</p>
          ) : (
            <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[22px]">
              {visible.map((drop) => <DropCard key={drop.id} drop={drop} now={now} />)}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
