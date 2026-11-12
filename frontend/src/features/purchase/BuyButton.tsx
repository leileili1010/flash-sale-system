import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import type { Drop } from '../../api/types';
import { Button, ButtonLink } from '../../components/Button';
import { Countdown } from '../../components/Countdown';
import { CartIcon, SpinnerIcon } from '../../components/icons';
import { useAuth } from '../auth/AuthProvider';

export type BuyState =
  | { kind: 'upcoming'; startsInMs: number }
  | { kind: 'ended' }
  | { kind: 'soldOut' }
  | { kind: 'owned'; orderNo: string; paid: boolean }
  | { kind: 'rateLimited'; retryInSeconds: number }
  | { kind: 'reserving' }
  | { kind: 'available'; error: string | null };

interface DeriveInput {
  drop: Drop;
  now: number;
  reserving: boolean;
  error: ApiError | null;
  ownedOrder: { orderNo: string; paid: boolean } | null;
  rateLimitedUntil: number | null;
}

/** Turns the drop, the clock and the last purchase attempt into exactly one button state. */
export function deriveBuyState({ drop, now, reserving, error, ownedOrder, rateLimitedUntil }: DeriveInput): BuyState {
  if (ownedOrder) return { kind: 'owned', ...ownedOrder };
  const start = new Date(drop.startTime).getTime();
  const end = new Date(drop.endTime).getTime();
  if (now < start) return { kind: 'upcoming', startsInMs: start - now };
  if (now >= end || error?.code === 'DROP_ENDED') return { kind: 'ended' };
  if (reserving) return { kind: 'reserving' };
  if (error?.code === 'SOLD_OUT' || drop.availableStock <= 0) return { kind: 'soldOut' };
  if (rateLimitedUntil !== null && now < rateLimitedUntil) {
    return { kind: 'rateLimited', retryInSeconds: Math.ceil((rateLimitedUntil - now) / 1000) };
  }
  return { kind: 'available', error: error && error.code !== 'RATE_LIMITED' ? error.message : null };
}

export function BuyButton({ drop, now }: { drop: Drop; now: number }) {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [rateLimitedUntil, setRateLimitedUntil] = useState<number | null>(null);

  const orders = useQuery({ queryKey: ['orders'], queryFn: endpoints.orders, enabled: isAuthenticated });
  const live = orders.data?.find((o) => o.dropId === drop.id && (o.status === 'PENDING_PAYMENT' || o.status === 'PAID'));

  const purchase = useMutation({
    mutationFn: () => endpoints.purchase(drop.id),
    onSuccess: (accepted) => {
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      void navigate(`/checkout/${accepted.orderNo}`);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'RATE_LIMITED') {
        setRateLimitedUntil(Date.now() + (error.retryAfterSeconds ?? 2) * 1000);
      }
      if (error instanceof ApiError && (error.code === 'SOLD_OUT' || error.code === 'ALREADY_PURCHASED')) {
        void queryClient.invalidateQueries({ queryKey: ['drop', drop.id] });
        void queryClient.invalidateQueries({ queryKey: ['orders'] });
      }
    },
  });

  // Clear a rate-limit lockout once it has passed, so the button re-enables by itself.
  useEffect(() => {
    if (rateLimitedUntil === null) return;
    const timer = window.setTimeout(() => setRateLimitedUntil(null), Math.max(0, rateLimitedUntil - Date.now()));
    return () => window.clearTimeout(timer);
  }, [rateLimitedUntil]);

  const error = purchase.error instanceof ApiError ? purchase.error : null;
  const alreadyPurchased = error?.code === 'ALREADY_PURCHASED';
  const state = deriveBuyState({
    drop,
    now,
    reserving: purchase.isPending,
    error,
    ownedOrder: live ? { orderNo: live.orderNo, paid: live.status === 'PAID' } : null,
    rateLimitedUntil,
  });

  function buy() {
    if (!isAuthenticated) {
      void navigate(`/login?next=${encodeURIComponent(`/drops/${drop.id}`)}`);
      return;
    }
    purchase.mutate();
  }

  const wide = 'w-full';
  switch (state.kind) {
    case 'upcoming':
      return (
        <Button size="lg" variant="muted" disabled className={wide}>
          Starts in&nbsp;<Countdown variant="inline" remainingMs={state.startsInMs} label="Starts in" />
        </Button>
      );
    case 'ended':
      return <Button size="lg" variant="muted" disabled className={wide}>This drop has ended</Button>;
    case 'soldOut':
      return <Button size="lg" variant="muted" disabled className={wide}>Sold out</Button>;
    case 'owned':
      return state.paid ? (
        <ButtonLink to="/orders" size="lg" variant="outline" className={wide}>You own this · View order</ButtonLink>
      ) : (
        <ButtonLink to={`/checkout/${state.orderNo}`} size="lg" variant="success" className={wide}>
          Reserved! Pay now
        </ButtonLink>
      );
    case 'rateLimited':
      return (
        <Button size="lg" variant="warn" disabled className={wide}>
          Busy, retry in {state.retryInSeconds}s
        </Button>
      );
    case 'reserving':
      return <Button size="lg" disabled className={wide} icon={<SpinnerIcon size={20} />}>Reserving…</Button>;
    case 'available':
      return (
        <div className="flex flex-col gap-2">
          {alreadyPurchased ? (
            <ButtonLink to="/orders" size="lg" variant="outline" className={wide}>You already have this · View orders</ButtonLink>
          ) : (
            <Button size="lg" onClick={buy} className={wide} icon={<CartIcon size={20} />}>
              {isAuthenticated ? 'Buy now' : 'Sign in to buy'}
            </Button>
          )}
          {state.error && !alreadyPurchased && <p role="alert" className="text-sm font-medium text-pink-600">{state.error}</p>}
        </div>
      );
  }
}
