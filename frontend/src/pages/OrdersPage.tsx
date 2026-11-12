import { useQueries, useQuery } from '@tanstack/react-query';
import { endpoints } from '../api/endpoints';
import type { Drop } from '../api/types';
import { Badge } from '../components/Badge';
import { ButtonLink } from '../components/Button';
import { ProductImage } from '../components/ProductImage';
import { displayTitle } from '../features/drops/labels';
import { presentStatus } from '../features/orders/orderStatus';
import { formatDateTime, formatMoney, formatShortDuration } from '../lib/format';
import { useServerNow } from '../lib/serverClock';

export function OrdersPage() {
  const now = useServerNow();
  const orders = useQuery({ queryKey: ['orders'], queryFn: endpoints.orders, refetchInterval: 10_000 });

  const dropIds = [...new Set(orders.data?.map((o) => o.dropId) ?? [])];
  const drops = useQueries({
    queries: dropIds.map((id) => ({ queryKey: ['drop', id], queryFn: () => endpoints.drop(id), staleTime: 60_000 })),
  });
  const dropById = new Map<number, Drop>();
  drops.forEach((q) => q.data && dropById.set(q.data.id, q.data));

  if (orders.isPending) {
    return <div aria-busy="true" aria-label="Loading orders" className="mx-auto mt-6 h-80 max-w-[1000px] animate-pulse rounded-3xl bg-white shadow-card" />;
  }
  if (orders.isError) {
    return <p role="alert" className="mx-auto mt-6 max-w-[1000px] rounded-3xl bg-white p-8 shadow-card">{orders.error.message}</p>;
  }

  return (
    <div className="mx-auto flex max-w-[1000px] flex-col gap-6 pt-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="font-display text-[40px] font-bold">My orders</h1>
        <span className="text-sm text-muted">{orders.data.length} {orders.data.length === 1 ? 'order' : 'orders'}</span>
      </div>

      {orders.data.length === 0 ? (
        <div className="rounded-3xl bg-white p-10 text-center shadow-card">
          <p className="font-display text-2xl font-bold">No orders yet</p>
          <p className="mt-1 text-muted">When you grab a figure, it shows up here.</p>
          <ButtonLink to="/" className="mt-6">Browse drops</ButtonLink>
        </div>
      ) : (
        <ul className="flex flex-col gap-3.5">
          {orders.data.map((order) => {
            const drop = dropById.get(order.dropId);
            const status = presentStatus(order);
            const payable = order.status === 'PENDING_PAYMENT' && order.expiresAt && new Date(order.expiresAt).getTime() > now;
            return (
              <li key={order.orderNo} className="flex flex-wrap items-center gap-[18px] rounded-3xl bg-white p-[18px] shadow-card">
                <ProductImage src={drop?.product.imageUrl ?? ''} alt={drop?.product.name ?? 'Product'} className="h-[84px] w-[92px] flex-none rounded-2xl" />
                <div className="flex min-w-0 flex-[1_1_260px] flex-col gap-1">
                  <span className="font-display text-[19px] font-bold">{drop ? displayTitle(drop.title, drop.product.series) : `Drop ${order.dropId}`}</span>
                  <span className="text-[13px] text-muted tabular">#{order.orderNo}{order.createdAt && ` · ${formatDateTime(order.createdAt)}`}</span>
                  <span className="text-[13px] text-muted">{status.detail}</span>
                </div>
                <div className="flex flex-none flex-col items-end gap-2">
                  <Badge tone={status.tone} className="px-3 text-[13px]">{status.label}</Badge>
                  {order.amount !== null && <span className="text-lg font-bold">{formatMoney(order.amount)}</span>}
                </div>
                {payable && order.expiresAt && (
                  <ButtonLink to={`/checkout/${order.orderNo}`} className="flex-none tabular">
                    Pay · {formatShortDuration(new Date(order.expiresAt).getTime() - now)} left
                  </ButtonLink>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <p className="text-[13px] text-muted">Expired and failed orders never charge you. Their units go back to stock automatically.</p>
    </div>
  );
}
