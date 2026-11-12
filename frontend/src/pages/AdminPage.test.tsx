import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { endpoints } from '../api/endpoints';
import type { AdminDrop, StockReport } from '../api/types';
import { RequireAdmin } from '../features/auth/RequireAuth';
import { renderPage, signIn, testDrop } from '../test/render';
import { AdminPage } from './AdminPage';

afterEach(() => window.localStorage.clear());

const now = Date.now();
function adminDrop(overrides: Partial<AdminDrop>): AdminDrop {
  return {
    id: 1, title: 'Pip Thistle Release', productId: 2, productName: 'Pip Thistle', imageUrl: '/images/p.webp',
    status: 'PUBLISHED', phase: 'UPCOMING', startTime: new Date(now + 3_600_000).toISOString(),
    endTime: new Date(now + 7_200_000).toISOString(), dropPrice: 12.49, originalPrice: 14.99,
    totalStock: 60, availableStock: 60, lockedStock: 0, soldStock: 0, ...overrides,
  };
}

function renderAdmin(route = '/admin') {
  return renderPage(<RequireAdmin><AdminPage /></RequireAdmin>, {
    path: '/admin', route, extraRoutes: [{ path: '/login', element: <p>login page</p> }],
  });
}

describe('AdminPage', () => {
  it('is reserved for admins', async () => {
    signIn('USER');
    renderAdmin();
    expect(await screen.findByRole('heading', { name: 'Admins only' })).toBeInTheDocument();
  });

  it('sends signed-out visitors to sign in', async () => {
    renderAdmin();
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('publishes a draft and cancels an upcoming drop after confirmation', async () => {
    signIn('ADMIN');
    vi.spyOn(endpoints.admin, 'drops').mockResolvedValue([
      adminDrop({ id: 1, title: 'Draft drop', status: 'DRAFT' }),
      adminDrop({ id: 2, title: 'Live drop', phase: 'LIVE', availableStock: 40, lockedStock: 5, soldStock: 15 }),
      adminDrop({ id: 3, title: 'Upcoming drop' }),
    ]);
    const publish = vi.spyOn(endpoints.admin, 'publishDrop').mockResolvedValue(adminDrop({ id: 1, title: 'Draft drop' }));
    const cancel = vi.spyOn(endpoints.admin, 'cancelDrop').mockResolvedValue(adminDrop({ id: 3, title: 'Upcoming drop', status: 'CANCELLED' }));
    renderAdmin();

    const rows = await screen.findAllByRole('row');
    const draftRow = rows.find((r) => within(r).queryByText('Draft drop'))!;
    const liveRow = rows.find((r) => within(r).queryByText('Live drop'))!;
    const upcomingRow = rows.find((r) => within(r).queryByText('Upcoming drop'))!;

    expect(within(liveRow).getByText('40')).toBeInTheDocument();
    expect(within(liveRow).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();

    await userEvent.click(within(draftRow).getByRole('button', { name: 'Publish' }));
    expect(publish).toHaveBeenCalledWith(1, expect.anything());
    expect(await screen.findByRole('status')).toHaveTextContent('“Draft drop” is now published.');

    await userEvent.click(within(upcomingRow).getByRole('button', { name: 'Cancel' }));
    expect(cancel).not.toHaveBeenCalled();
    await userEvent.click(within(upcomingRow).getByRole('button', { name: 'Confirm cancel' }));
    expect(cancel).toHaveBeenCalledWith(3, expect.anything());
  });

  it('creates a drop from the form', async () => {
    signIn('ADMIN');
    vi.spyOn(endpoints, 'products').mockResolvedValue([testDrop().product]);
    vi.spyOn(endpoints.admin, 'drops').mockResolvedValue([]);
    const create = vi.spyOn(endpoints.admin, 'createDrop').mockResolvedValue(adminDrop({ title: 'Forest Friends: Mochi Moss Drop' }));
    renderAdmin('/admin?tab=new');

    await screen.findByRole('option', { name: /Mochi Moss · Forest Friends/ });
    await userEvent.selectOptions(screen.getByLabelText('Product'), '1');
    expect(screen.getByLabelText('Title')).toHaveValue('Forest Friends: Mochi Moss Drop');
    expect(screen.getByLabelText(/Drop price/)).toHaveValue(14.99);
    await userEvent.clear(screen.getByLabelText(/Drop price/));
    await userEvent.type(screen.getByLabelText(/Drop price/), '11.99');
    expect(screen.getByText('20% off retail')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Create and publish' }));

    expect(create).toHaveBeenCalledWith(expect.objectContaining({
      productId: 1, title: 'Forest Friends: Mochi Moss Drop', dropPrice: 11.99, totalStock: 50, publish: true,
    }), expect.anything());
    expect(await screen.findByRole('status')).toHaveTextContent('“Forest Friends: Mochi Moss Drop” is published.');
    expect(screen.getByRole('tab', { name: 'Drops' })).toHaveAttribute('aria-selected', 'true');
  });

  it('shows Redis drift and offers a resync', async () => {
    signIn('ADMIN');
    const report = (overrides: Partial<StockReport>): StockReport => ({
      dropId: 1, title: 'Mochi Moss Launch', totalStock: 100, databaseAvailable: 90, databaseLocked: 6, databaseSold: 4,
      redisStock: 88, redisBuyers: 12, liveOrders: 10, inFlightReservations: 2, drift: 0, ...overrides,
    });
    vi.spyOn(endpoints.admin, 'inventory').mockResolvedValue([
      report({}),
      report({ dropId: 2, title: 'Dumpling Dot', redisStock: 120, drift: 30 }),
    ]);
    const resync = vi.spyOn(endpoints.admin, 'resync').mockResolvedValue(report({ dropId: 2 }));
    renderAdmin('/admin?tab=inventory');

    expect(await screen.findByRole('status')).toHaveTextContent('1 of 2 active drops show drift');
    expect(screen.getByText('0 · consistent')).toBeInTheDocument();
    expect(screen.getByText('+30')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Resync' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm resync' }));
    expect(resync).toHaveBeenCalledWith(2, expect.anything());
  });
});
