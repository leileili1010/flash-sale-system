import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { endpoints } from '../api/endpoints';
import type { Drop } from '../api/types';
import { HomePage } from './HomePage';

function drop(overrides: Partial<Drop> & Pick<Drop, 'id' | 'title' | 'startTime' | 'endTime'>): Drop {
  return {
    product: {
      id: overrides.id, sku: `SKU-${overrides.id}`, name: overrides.title, series: 'Forest Friends', type: 'BLIND_BOX',
      description: 'A sleepy forest sprite.', imageUrl: '/images/products/mochi-moss.png', retailPrice: 14.99,
    },
    dropPrice: 11.99, originalPrice: 14.99, totalStock: 100, availableStock: 62, phase: 'LIVE', soldOut: false,
    ...overrides,
  };
}

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/', element: <HomePage /> }]);
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe('HomePage', () => {
  it('features the next upcoming drop and lists live and upcoming drops', async () => {
    const now = Date.now();
    const iso = (offsetMs: number) => new Date(now + offsetMs).toISOString();
    vi.spyOn(endpoints, 'drops').mockResolvedValue([
      drop({ id: 1, title: 'Mochi Moss Launch', startTime: iso(-60_000), endTime: iso(3_600_000) }),
      drop({ id: 2, title: 'Forest Friends: Neon Noodle Midnight Drop', startTime: iso(120_000), endTime: iso(7_200_000), phase: 'UPCOMING' }),
      drop({ id: 3, title: 'Coral Kip Plush', startTime: iso(-7_200_000), endTime: iso(-3_600_000), phase: 'ENDED' }),
    ]);

    renderHome();

    const hero = await screen.findByRole('region', { name: 'Neon Noodle Midnight Drop' });
    expect(within(hero).getByRole('timer')).toBeInTheDocument();

    const live = screen.getByRole('region', { name: /Live now/ });
    expect(within(live).getByText('Mochi Moss Launch')).toBeInTheDocument();
    expect(within(live).getByRole('link', { name: 'Buy now' })).toHaveAttribute('href', '/drops/1');
    expect(screen.queryByText('Coral Kip Plush')).not.toBeInTheDocument();

    const upcoming = screen.getByRole('region', { name: /Coming up/ });
    expect(within(upcoming).getByText('Starts in 2m')).toBeInTheDocument();
  });

  it('shows an error state when drops cannot be loaded', async () => {
    vi.spyOn(endpoints, 'drops').mockRejectedValue(new Error('Could not reach the server.'));
    renderHome();
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server.');
  });
});
