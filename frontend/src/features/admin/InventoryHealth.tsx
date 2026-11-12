import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { endpoints } from '../../api/endpoints';
import type { StockReport } from '../../api/types';
import { Badge } from '../../components/Badge';

/**
 * Compares Redis with the database for every active drop. Redis is ahead of the database by the
 * in-flight reservations (orders still in Kafka), so expected Redis stock = database available - in flight.
 */
export function InventoryHealth() {
  const queryClient = useQueryClient();
  const report = useQuery({ queryKey: ['admin', 'inventory'], queryFn: endpoints.admin.inventory, refetchInterval: 5_000 });
  const [confirm, setConfirm] = useState<number | null>(null);
  const resync = useMutation({
    mutationFn: endpoints.admin.resync,
    onSuccess: () => {
      setConfirm(null);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
    },
  });

  if (report.isPending) return <div aria-busy="true" aria-label="Loading inventory" className="h-64 animate-pulse rounded-3xl bg-white shadow-card" />;
  if (report.isError) return <p role="alert" className="rounded-3xl bg-white p-8 shadow-card">{report.error.message}</p>;

  const drifting = report.data.filter((r) => r.drift !== null && r.drift !== 0).length;

  return (
    <div className="flex flex-col gap-4">
      <p role="status" className={`rounded-2xl px-4 py-3 text-sm font-medium ${drifting === 0 ? 'bg-live-ring text-live-ink' : 'bg-warn-bg text-warn-ink'}`}>
        {drifting === 0
          ? `All ${report.data.length} active drops are consistent between Redis and the database.`
          : `${drifting} of ${report.data.length} active drops show drift. Resync only when the drop is quiet.`}
      </p>
      <div className="overflow-x-auto rounded-3xl bg-white shadow-card">
        <table className="w-full min-w-[860px] text-left text-sm tabular">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-5 py-3.5 font-semibold">Drop</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">DB available</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">DB locked / sold</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Redis stock</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">In flight</th>
              <th scope="col" className="px-3 py-3.5 font-semibold">Drift</th>
              <th scope="col" className="px-5 py-3.5 text-right font-semibold"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {report.data.map((row) => (
              <tr key={row.dropId} className="border-b border-line last:border-0">
                <td className="px-5 py-3.5"><span className="font-bold">{row.title}</span><p className="text-xs text-muted">#{row.dropId} · {row.totalStock} total</p></td>
                <td className="px-3 py-3.5">{row.databaseAvailable}</td>
                <td className="px-3 py-3.5">{row.databaseLocked} / {row.databaseSold}</td>
                <td className="px-3 py-3.5">{row.redisStock ?? <span className="text-subtle">not loaded</span>}</td>
                <td className="px-3 py-3.5">{row.inFlightReservations}</td>
                <td className="px-3 py-3.5"><DriftBadge row={row} /></td>
                <td className="px-5 py-3.5 text-right">
                  {row.drift !== null && row.drift !== 0 && (
                    confirm === row.dropId ? (
                      <button type="button" onClick={() => resync.mutate(row.dropId)} disabled={resync.isPending}
                              className="rounded-full bg-ink px-4 py-2 text-xs font-bold text-white disabled:opacity-60">
                        Confirm resync
                      </button>
                    ) : (
                      <button type="button" onClick={() => setConfirm(row.dropId)}
                              className="rounded-full border border-line px-4 py-2 text-xs font-bold text-muted hover:border-ink hover:text-ink">
                        Resync
                      </button>
                    )
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function DriftBadge({ row }: { row: StockReport }) {
  if (row.drift === null) return <Badge tone="neutral">n/a</Badge>;
  if (row.drift === 0) return <Badge tone="success">0 · consistent</Badge>;
  return <Badge tone="warn">{row.drift > 0 ? '+' : ''}{row.drift}</Badge>;
}
