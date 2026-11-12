import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import type { AdminDrop } from '../../api/types';
import { Badge, type BadgeTone } from '../../components/Badge';
import { ProductImage } from '../../components/ProductImage';
import { formatDateTime, formatMoney } from '../../lib/format';

const statusTone: Record<AdminDrop['status'], BadgeTone> = { DRAFT: 'warn', PUBLISHED: 'success', CANCELLED: 'neutral' };
const phaseTone: Record<AdminDrop['phase'], BadgeTone> = { LIVE: 'live', UPCOMING: 'chip', ENDED: 'neutral' };

export function DropsTable() {
  const queryClient = useQueryClient();
  const drops = useQuery({ queryKey: ['admin', 'drops'], queryFn: endpoints.admin.drops, refetchInterval: 10_000 });
  const [confirmCancel, setConfirmCancel] = useState<number | null>(null);
  const [message, setMessage] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin'] });
    void queryClient.invalidateQueries({ queryKey: ['drops'] });
  };
  const onError = (error: Error) => setMessage({ tone: 'error', text: error instanceof ApiError ? error.message : 'Something went wrong.' });

  const publish = useMutation({
    mutationFn: endpoints.admin.publishDrop,
    onSuccess: (drop) => { setMessage({ tone: 'ok', text: `“${drop.title}” is now published.` }); refresh(); },
    onError,
  });
  const cancel = useMutation({
    mutationFn: endpoints.admin.cancelDrop,
    onSuccess: (drop) => { setConfirmCancel(null); setMessage({ tone: 'ok', text: `“${drop.title}” was cancelled.` }); refresh(); },
    onError,
  });

  if (drops.isPending) return <div aria-busy="true" aria-label="Loading drops" className="h-64 animate-pulse rounded-3xl bg-white shadow-card" />;
  if (drops.isError) return <p role="alert" className="rounded-3xl bg-white p-8 shadow-card">{drops.error.message}</p>;

  return (
    <div className="flex flex-col gap-4">
      {message && (
        <p role="status" className={`rounded-2xl px-4 py-3 text-sm font-medium ${message.tone === 'ok' ? 'bg-live-ring text-live-ink' : 'bg-pink-50 text-[#A02656]'}`}>
          {message.text}
        </p>
      )}
      <div className="overflow-x-auto rounded-3xl bg-white shadow-card">
        <table className="w-full min-w-[960px] text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-5 py-3.5 font-semibold">Drop</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Status</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Schedule</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Price</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Stock (available / locked / sold)</th>
              <th scope="col" className="px-5 py-3.5 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody>
            {drops.data.map((drop) => (
              <tr key={drop.id} className="border-b border-line last:border-0">
                <td className="px-5 py-3.5">
                  <div className="flex items-center gap-3">
                    <ProductImage src={drop.imageUrl} alt="" className="size-12 flex-none rounded-xl" />
                    <div className="min-w-0">
                      {drop.status === 'PUBLISHED'
                        ? <Link to={`/drops/${drop.id}`} className="font-bold hover:text-pink-600">{drop.title}</Link>
                        : <span className="font-bold">{drop.title}</span>}
                      <p className="text-xs text-muted">#{drop.id} · {drop.productName}</p>
                    </div>
                  </div>
                </td>
                <td className="px-3 py-3.5">
                  <div className="flex flex-col items-start gap-1">
                    <Badge tone={statusTone[drop.status]}>{drop.status}</Badge>
                    {drop.status === 'PUBLISHED' && <Badge tone={phaseTone[drop.phase]}>{drop.phase}</Badge>}
                  </div>
                </td>
                <td className="px-3 py-3.5 text-xs leading-relaxed text-muted tabular">
                  {formatDateTime(drop.startTime)}<br />→ {formatDateTime(drop.endTime)}
                </td>
                <td className="px-3 py-3.5 font-semibold tabular">{formatMoney(drop.dropPrice)}</td>
                <td className="px-3 py-3.5"><StockBreakdown drop={drop} /></td>
                <td className="px-5 py-3.5">
                  <div className="flex justify-end gap-2">
                    {drop.status === 'DRAFT' && drop.phase !== 'ENDED' && (
                      <button type="button" onClick={() => publish.mutate(drop.id)} disabled={publish.isPending}
                              className="rounded-full bg-cta px-4 py-2 text-xs font-bold text-white disabled:opacity-60">
                        Publish
                      </button>
                    )}
                    {drop.status !== 'CANCELLED' && drop.phase === 'UPCOMING' && (
                      confirmCancel === drop.id ? (
                        <>
                          <button type="button" onClick={() => cancel.mutate(drop.id)} disabled={cancel.isPending}
                                  className="rounded-full bg-ink px-4 py-2 text-xs font-bold text-white disabled:opacity-60">
                            Confirm cancel
                          </button>
                          <button type="button" onClick={() => setConfirmCancel(null)} className="rounded-full px-3 py-2 text-xs font-bold text-muted">
                            Keep
                          </button>
                        </>
                      ) : (
                        <button type="button" onClick={() => setConfirmCancel(drop.id)}
                                className="rounded-full border border-line px-4 py-2 text-xs font-bold text-muted hover:border-ink hover:text-ink">
                          Cancel
                        </button>
                      )
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StockBreakdown({ drop }: { drop: AdminDrop }) {
  const pct = (n: number) => (drop.totalStock > 0 ? (n / drop.totalStock) * 100 : 0);
  return (
    <div className="flex min-w-[200px] flex-col gap-1.5">
      <div className="flex h-2 overflow-hidden rounded-full bg-track" aria-hidden="true">
        <div className="bg-[#8FB4FF]" style={{ width: `${pct(drop.availableStock)}%` }} />
        <div className="bg-[#FFB547]" style={{ width: `${pct(drop.lockedStock)}%` }} />
        <div className="bg-pink-500" style={{ width: `${pct(drop.soldStock)}%` }} />
      </div>
      <p className="text-xs text-muted tabular">
        <span className="font-semibold text-ink">{drop.availableStock}</span> / {drop.lockedStock} / {drop.soldStock}
        <span className="text-subtle"> of {drop.totalStock}</span>
      </p>
    </div>
  );
}
