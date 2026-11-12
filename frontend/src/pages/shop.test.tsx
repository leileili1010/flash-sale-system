import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { endpoints } from '../api/endpoints';
import type { Order } from '../api/types';
import { authResponse, renderPage, signIn, testDrop } from '../test/render';
import { CheckoutPage } from './CheckoutPage';
import { LoginPage } from './LoginPage';
import { OrdersPage } from './OrdersPage';

afterEach(() => window.localStorage.clear());

function order(overrides: Partial<Order> = {}): Order {
  const now = Date.now();
  return {
    orderNo: '114454896836612096', dropId: 1, status: 'PENDING_PAYMENT', amount: 11.99, failureReason: null,
    createdAt: new Date(now).toISOString(), expiresAt: new Date(now + 9 * 60_000).toISOString(), paidAt: null,
    ...overrides,
  };
}

describe('LoginPage', () => {
  it('signs in with the demo account and returns to the requested page', async () => {
    const login = vi.spyOn(endpoints, 'login').mockResolvedValue(authResponse());
    const { router } = renderPage(<LoginPage />, {
      path: '/login', route: '/login?next=%2Fdrops%2F1', extraRoutes: [{ path: '/drops/:id', element: <p>drop page</p> }],
    });

    await userEvent.click(screen.getByRole('button', { name: 'Use the demo shopper account' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(login).toHaveBeenCalledWith('shopper@fluffle.dev', 'shopper-password');
    expect(await screen.findByText('drop page')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/drops/1');
  });

  it('shows the server message for wrong credentials', async () => {
    vi.spyOn(endpoints, 'login').mockRejectedValue(new ApiError('INVALID_CREDENTIALS', 401, 'The email or password is incorrect.'));
    renderPage(<LoginPage />, { path: '/login', route: '/login' });

    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'nope-nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.');
  });

  it('ignores off-site redirect targets', async () => {
    vi.spyOn(endpoints, 'login').mockResolvedValue(authResponse());
    const { router } = renderPage(<LoginPage />, {
      path: '/login', route: '/login?next=%2F%2Fevil.example', extraRoutes: [{ path: '/', element: <p>home</p> }],
    });
    await userEvent.click(screen.getByRole('button', { name: 'Use the demo shopper account' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('home')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});

describe('CheckoutPage', () => {
  it('shows the payment timer and completes payment', async () => {
    signIn();
    vi.spyOn(endpoints, 'order').mockResolvedValue(order());
    vi.spyOn(endpoints, 'drop').mockResolvedValue(testDrop());
    const pay = vi.spyOn(endpoints, 'pay').mockResolvedValue(order({ status: 'PAID', paidAt: new Date().toISOString() }));
    renderPage(<CheckoutPage />, { path: '/checkout/:orderNo', route: '/checkout/114454896836612096' });

    expect(await screen.findByRole('heading', { name: 'You got one!' })).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveAccessibleName(/minutes .* seconds left to pay/);
    expect(await screen.findByText('Mochi Moss Launch')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Pay $11.99' }));

    expect(pay).toHaveBeenCalledWith('114454896836612096');
    expect(await screen.findByRole('heading', { name: 'Payment received' })).toBeInTheDocument();
  });

  it('waits while the order is still being created', async () => {
    signIn();
    vi.spyOn(endpoints, 'order').mockResolvedValue(order({ status: 'QUEUED', dropId: 0, amount: null, expiresAt: null, createdAt: null }));
    renderPage(<CheckoutPage />, { path: '/checkout/:orderNo', route: '/checkout/1' });
    expect(await screen.findByRole('heading', { name: 'Creating your order…' })).toBeInTheDocument();
  });

  it('explains an expired reservation', async () => {
    signIn();
    vi.spyOn(endpoints, 'order').mockResolvedValue(order({ status: 'CLOSED' }));
    vi.spyOn(endpoints, 'drop').mockResolvedValue(testDrop());
    renderPage(<CheckoutPage />, { path: '/checkout/:orderNo', route: '/checkout/1' });
    expect(await screen.findByRole('heading', { name: 'Reservation expired' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Pay/ })).not.toBeInTheDocument();
  });
});

describe('OrdersPage', () => {
  it('lists orders with their status and a pay shortcut for pending ones', async () => {
    signIn();
    vi.spyOn(endpoints, 'orders').mockResolvedValue([
      order(),
      order({ orderNo: '2', status: 'PAID' }),
      order({ orderNo: '3', status: 'FAILED', failureReason: 'SOLD_OUT' }),
    ]);
    vi.spyOn(endpoints, 'drop').mockResolvedValue(testDrop());
    renderPage(<OrdersPage />, { path: '/orders', route: '/orders' });

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(within(items[0]!).getByText('Pending payment')).toBeInTheDocument();
    expect(within(items[0]!).getByRole('link', { name: /Pay · \d+m left/ })).toHaveAttribute('href', '/checkout/114454896836612096');
    expect(within(items[1]!).getByText('Paid')).toBeInTheDocument();
    expect(within(items[2]!).getByText(/Not charged/)).toBeInTheDocument();
  });
});
