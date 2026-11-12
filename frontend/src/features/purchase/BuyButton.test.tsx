import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import { renderPage, signIn, testDrop } from '../../test/render';
import { BuyButton, deriveBuyState } from './BuyButton';

const now = Date.now();
const base = { now, reserving: false, error: null, ownedOrder: null, rateLimitedUntil: null };

describe('deriveBuyState', () => {
  it('counts down before the drop starts', () => {
    const drop = testDrop({ startTime: new Date(now + 90_000).toISOString() });
    expect(deriveBuyState({ ...base, drop })).toEqual({ kind: 'upcoming', startsInMs: 90_000 });
  });

  it('is available while live with stock', () => {
    expect(deriveBuyState({ ...base, drop: testDrop() })).toEqual({ kind: 'available', error: null });
  });

  it('is sold out when stock reaches zero or the server says so', () => {
    expect(deriveBuyState({ ...base, drop: testDrop({ availableStock: 0 }) }).kind).toBe('soldOut');
    const error = new ApiError('SOLD_OUT', 409, 'This item is sold out.');
    expect(deriveBuyState({ ...base, drop: testDrop(), error }).kind).toBe('soldOut');
  });

  it('shows an existing order instead of a buy button', () => {
    const state = deriveBuyState({ ...base, drop: testDrop(), ownedOrder: { orderNo: '42', paid: false } });
    expect(state).toEqual({ kind: 'owned', orderNo: '42', paid: false });
  });

  it('locks the button while rate limited and reports seconds left', () => {
    const state = deriveBuyState({ ...base, drop: testDrop(), rateLimitedUntil: now + 2_500 });
    expect(state).toEqual({ kind: 'rateLimited', retryInSeconds: 3 });
  });

  it('is ended after the window closes', () => {
    const drop = testDrop({ endTime: new Date(now - 1).toISOString() });
    expect(deriveBuyState({ ...base, drop }).kind).toBe('ended');
  });
});

describe('BuyButton', () => {
  afterEach(() => window.localStorage.clear());

  it('sends signed-out shoppers to sign in first', async () => {
    const drop = testDrop();
    const { router } = renderPage(<BuyButton drop={drop} now={Date.now()} />, {
      path: '/drops/:dropId', route: '/drops/1', extraRoutes: [{ path: '/login', element: <p>login page</p> }],
    });

    await userEvent.click(screen.getByRole('button', { name: 'Sign in to buy' }));

    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?next=%2Fdrops%2F1');
  });

  it('reserves and goes to checkout', async () => {
    signIn();
    vi.spyOn(endpoints, 'orders').mockResolvedValue([]);
    vi.spyOn(endpoints, 'purchase').mockResolvedValue({ orderNo: '114454896836612096', dropId: 1, status: 'QUEUED' });
    const { router } = renderPage(<BuyButton drop={testDrop()} now={Date.now()} />, {
      path: '/drops/:dropId', route: '/drops/1', extraRoutes: [{ path: '/checkout/:orderNo', element: <p>checkout</p> }],
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Buy now' }));

    expect(await screen.findByText('checkout')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/checkout/114454896836612096');
  });

  it('explains a rate-limit rejection', async () => {
    signIn();
    vi.spyOn(endpoints, 'orders').mockResolvedValue([]);
    vi.spyOn(endpoints, 'purchase').mockRejectedValue(new ApiError('RATE_LIMITED', 429, 'Too many requests.', {}, 3));
    renderPage(<BuyButton drop={testDrop()} now={Date.now()} />, { path: '/drops/:dropId', route: '/drops/1' });

    await userEvent.click(await screen.findByRole('button', { name: 'Buy now' }));

    expect(await screen.findByRole('button', { name: /Busy, retry in \d+s/ })).toBeDisabled();
  });
});
