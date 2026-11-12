import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { endpoints } from '../api/endpoints';
import type { Product } from '../api/types';
import { renderPage, testDrop } from '../test/render';
import { AllDropsPage } from './AllDropsPage';
import { DropDetailPage } from './DropDetailPage';

const now = Date.now();
const iso = (offsetMs: number) => new Date(now + offsetMs).toISOString();

function product(id: number, name: string, series = 'Forest Friends'): Product {
  return { id, sku: `SKU-${id}`, name, series, type: 'BLIND_BOX', description: '', imageUrl: `/images/${id}.png`, retailPrice: 14.99 };
}

const mochi = product(1, 'Mochi Moss');
const pip = product(2, 'Pip Thistle');
const juniper = product(4, 'Juniper Snooze');
const grand = product(10, 'Mochi Moss 400% Grand Edition');

const drops = [
  testDrop({ id: 1, title: 'Forest Friends: Mochi Moss Launch', product: mochi }),
  testDrop({ id: 4, title: 'Secret Edition: Juniper Snooze', product: juniper, startTime: iso(3_600_000), endTime: iso(7_200_000), phase: 'UPCOMING' }),
  testDrop({ id: 5, title: 'Mochi Moss 400% Grand Edition', product: grand, startTime: iso(86_400_000), endTime: iso(90_000_000), phase: 'UPCOMING' }),
  testDrop({ id: 6, title: 'Coral Kip Plush Preview', product: product(8, 'Coral Kip', 'Sea Breeze'), startTime: iso(-7_200_000), endTime: iso(-3_600_000), phase: 'ENDED' }),
];

describe('AllDropsPage', () => {
  it('groups every drop by phase with counts, and switches tabs', async () => {
    vi.spyOn(endpoints, 'drops').mockResolvedValue(drops);
    const { router } = renderPage(<AllDropsPage />, { path: '/drops', route: '/drops' });

    const live = await screen.findByRole('tab', { name: /Live now\s*1/ });
    expect(live).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: /Coming up\s*2/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Ended\s*1/ })).toBeInTheDocument();
    expect(within(screen.getByRole('tabpanel')).getByText('Mochi Moss Launch')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: /Coming up/ }));

    const panel = screen.getByRole('tabpanel', { name: 'Coming up' });
    const titles = within(panel).getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(titles).toEqual(['Secret Edition: Juniper Snooze', 'Mochi Moss 400% Grand Edition']);
    expect(within(panel).getAllByRole('link', { name: 'View drop' })[0]).toHaveAttribute('href', '/drops/4');
    expect(router.state.location.search).toBe('?phase=upcoming');
  });

  it('opens the tab named in the URL', async () => {
    vi.spyOn(endpoints, 'drops').mockResolvedValue(drops);
    renderPage(<AllDropsPage />, { path: '/drops', route: '/drops?phase=ended' });
    const panel = await screen.findByRole('tabpanel', { name: 'Ended' });
    expect(within(panel).getByText('Coral Kip Plush Preview')).toBeInTheDocument();
    expect(within(panel).getByRole('link', { name: 'View details' })).toHaveAttribute('href', '/drops/6');
  });
});

describe('Series lineup on the drop page', () => {
  it('links each character to its drop and marks those without one', async () => {
    vi.spyOn(endpoints, 'drop').mockResolvedValue(drops[0]!);
    vi.spyOn(endpoints, 'drops').mockResolvedValue(drops);
    vi.spyOn(endpoints, 'products').mockResolvedValue([mochi, pip, juniper, grand]);
    renderPage(<DropDetailPage />, { path: '/drops/:dropId', route: '/drops/1' });

    expect(await screen.findByRole('link', { name: 'Juniper Snooze, Coming soon' })).toHaveAttribute('href', '/drops/4');
    expect(screen.getByRole('link', { name: 'Mochi Moss 400% Grand Edition, Coming soon' })).toHaveAttribute('href', '/drops/5');
    expect(screen.queryByRole('link', { name: /Pip Thistle/ })).not.toBeInTheDocument();
    expect(screen.getByText('No drop yet')).toBeInTheDocument();
    expect(screen.getByText('Viewing')).toBeInTheDocument();
  });
});
