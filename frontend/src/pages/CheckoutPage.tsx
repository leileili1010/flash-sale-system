import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useParams } from 'react-router';
import { ApiError } from '../api/client';
import { endpoints } from '../api/endpoints';
import type { Order } from '../api/types';
import { Button, ButtonLink } from '../components/Button';
import { CardIcon, CheckIcon, ClockIcon, SpinnerIcon } from '../components/icons';
import { ProductImage } from '../components/ProductImage';
import { displayTitle, productTypeLabel } from '../features/drops/labels';
import { countdownParts, formatMoney } from '../lib/format';
import { useServerNow } from '../lib/serverClock';

export function CheckoutPage() {
  const orderNo = useParams().orderNo ?? '';
  const now = useServerNow();
  const queryClient = useQueryClient();

  const order = useQuery({
    queryKey: ['order', orderNo],
    queryFn: () => endpoints.order(orderNo),
    // Poll quickly while Kafka creates the order, slowly while awaiting payment, then stop.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'QUEUED' || status === undefined) return 750;
      return status === 'PENDING_PAYMENT' ? 5_000 : false;
    },
  });
  const dropId = order.data?.dropId ?? 0;
  const drop = useQuery({ queryKey: ['drop', dropId], queryFn: () => endpoints.drop(dropId), enabled: dropId > 0 });

  const pay = useMutation({
    mutationFn: () => endpoints.pay(orderNo),
    onSuccess: (paid) => {
      queryClient.setQueryData(['order', orderNo], paid);
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      void queryClient.invalidateQueries({ queryKey: ['drop', paid.dropId] });
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: ['order', orderNo] }),
  });

  if (order.isError) {
    return <StatePanel tone="neutral" title="We couldn't find this order" body={order.error.message} />;
  }
  if (order.isPending) {
    return <StatePanel tone="queued" title="Creating your order…" body="Your unit is reserved. This usually takes under a second." />;
  }

  const o = order.data;
  const remainingMs = o.expiresAt ? new Date(o.expiresAt).getTime() - now : 0;
  const expiredLocally = o.status === 'PENDING_PAYMENT' && remainingMs <= 0;

  return (
    <div className="mx-auto flex max-w-[1100px] flex-col gap-8 pt-6">
      <Progress order={o} />
      <div className="flex flex-wrap items-start gap-7">
        <section aria-labelledby="checkout-title" className="flex min-w-0 flex-[999_1_520px] flex-col gap-6 rounded-[32px] bg-white p-8 shadow-card">
          {o.status === 'QUEUED' && <Headline icon={<SpinnerIcon size={40} className="animate-spin text-chip-ink" />} title="Creating your order…" body="Your unit is reserved. This usually takes under a second." />}
          {o.status === 'PENDING_PAYMENT' && !expiredLocally && (
            <div className="flex flex-wrap items-center gap-6">
              <TimerRing remainingMs={remainingMs} />
              <div className="flex-[1_1_260px]">
                <h1 id="checkout-title" className="font-display text-[38px] font-bold leading-tight">You got one!</h1>
                <p className="mt-2 leading-relaxed text-muted">
                  Your figure is held for you. Complete payment before the timer runs out, or it goes back to stock for the next shopper.
                </p>
              </div>
            </div>
          )}
          {expiredLocally && <Headline icon={<ClockIcon size={40} className="text-warn-ink" />} title="Reservation expired" body="The payment window passed, so the unit is going back to stock." />}
          {o.status === 'PAID' && <Headline icon={<CheckIcon size={40} className="text-live-ink" />} title="Payment received" body="It's yours! You can find it in My Orders." />}
          {o.status === 'CLOSED' && <Headline icon={<ClockIcon size={40} className="text-warn-ink" />} title="Reservation expired" body="The unit went back to stock. If any remain, you can try again." />}
          {o.status === 'FAILED' && <Headline icon={<span className="text-4xl font-bold text-muted">×</span>} title="Couldn't complete your order" body="The last unit was taken before your order was confirmed. Nothing was charged." />}

          {drop.data && (
            <div className="flex items-center gap-4 rounded-[22px] bg-[#F3F6FD] p-4">
              <ProductImage src={drop.data.product.imageUrl} alt={drop.data.product.name} className="h-[88px] w-24 flex-none rounded-2xl" />
              <div className="min-w-0 flex-1">
                <p className="font-display text-[19px] font-bold">{displayTitle(drop.data.title, drop.data.product.series)}</p>
                <p className="text-sm text-muted">{drop.data.product.series} · {productTypeLabel(drop.data.product.type)} · Qty 1</p>
                <p className="text-[13px] text-subtle tabular">Order #{o.orderNo}</p>
              </div>
              <span className="text-[22px] font-bold">{formatMoney(o.amount ?? drop.data.dropPrice)}</span>
            </div>
          )}

          {o.status === 'PENDING_PAYMENT' && !expiredLocally && (
            <div className="flex flex-col gap-2.5">
              <Button size="lg" onClick={() => pay.mutate()} disabled={pay.isPending} className="w-full"
                      icon={pay.isPending ? <SpinnerIcon size={20} /> : <CardIcon size={20} />}>
                {pay.isPending ? 'Processing…' : `Pay ${o.amount !== null ? formatMoney(o.amount) : ''}`}
              </Button>
              {pay.error && <p role="alert" className="text-center text-sm font-medium text-pink-600">{pay.error instanceof ApiError ? pay.error.message : 'Payment failed.'}</p>}
              <p className="text-center text-[13px] text-muted">Demo checkout: no card is charged.</p>
            </div>
          )}
          {(o.status === 'PAID' || o.status === 'CLOSED' || o.status === 'FAILED' || expiredLocally) && (
            <div className="flex flex-wrap gap-3">
              <ButtonLink to="/orders" variant={o.status === 'PAID' ? 'primary' : 'outline'}>View my orders</ButtonLink>
              <ButtonLink to="/" variant="outline">Back to drops</ButtonLink>
            </div>
          )}
        </section>

        <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-3 rounded-[28px] bg-white/70 p-6 text-sm leading-relaxed text-muted shadow-card">
          <h2 className="font-display text-lg font-bold text-ink">How checkout works</h2>
          <p>Your figure is reserved the moment you click Buy. Nobody else can take it while your timer runs.</p>
          <p>If the timer runs out, the reservation is released automatically so another shopper gets a chance.</p>
          <p>Paying twice is safe: a retried payment never charges again.</p>
        </aside>
      </div>
    </div>
  );
}

function Progress({ order }: { order: Order }) {
  const created = order.status !== 'QUEUED';
  const paid = order.status === 'PAID';
  const steps: [string, boolean, boolean][] = [
    ['Unit reserved', true, false],
    ['Order created', created, !created],
    ['Payment', paid, created && !paid],
  ];
  return (
    <ol aria-label="Purchase progress" className="flex flex-wrap gap-2.5 text-sm font-semibold">
      {steps.map(([label, done, current]) => (
        <li key={label} aria-current={current ? 'step' : undefined}
            className={`flex items-center gap-2 rounded-full px-3.5 py-2 ${done ? 'bg-live-ring text-live-ink' : current ? 'bg-ink text-white' : 'bg-track text-muted'}`}>
          {done && <CheckIcon size={16} />}{label}
        </li>
      ))}
    </ol>
  );
}

function TimerRing({ remainingMs }: { remainingMs: number }) {
  const { minutes, seconds } = countdownParts(remainingMs);
  const fraction = Math.max(0, Math.min(1, remainingMs / 600_000));
  return (
    <div role="timer" aria-label={`${Number(minutes)} minutes ${Number(seconds)} seconds left to pay`}
         className="flex size-[132px] flex-none items-center justify-center rounded-full"
         style={{ background: `conic-gradient(#E0407A 0 ${fraction * 100}%, #E9EEF8 ${fraction * 100}% 100%)` }}>
      <div className="flex size-[108px] flex-col items-center justify-center rounded-full bg-white tabular">
        <span className="font-display text-[32px] font-bold">{minutes}:{seconds}</span>
        <span className="text-xs text-muted">left to pay</span>
      </div>
    </div>
  );
}

function Headline({ icon, title, body }: { icon: ReactNode; title: string; body: string }) {
  return (
    <div className="flex items-center gap-5">
      <div className="flex size-[72px] flex-none items-center justify-center rounded-full bg-[#F3F6FD]">{icon}</div>
      <div>
        <h1 id="checkout-title" className="font-display text-[32px] font-bold leading-tight">{title}</h1>
        <p className="mt-1 text-muted">{body}</p>
      </div>
    </div>
  );
}

function StatePanel({ title, body, tone }: { title: string; body: string; tone: 'queued' | 'neutral' }) {
  return (
    <section className="mx-auto mt-8 flex max-w-xl flex-col items-center gap-4 rounded-[32px] bg-white p-10 text-center shadow-card">
      {tone === 'queued' && <SpinnerIcon size={40} className="animate-spin text-chip-ink" />}
      <h1 className="font-display text-3xl font-bold">{title}</h1>
      <p className="text-muted">{body}</p>
      {tone === 'neutral' && <ButtonLink to="/orders" variant="outline">View my orders</ButtonLink>}
    </section>
  );
}
