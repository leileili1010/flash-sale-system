import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import type { AuthResponse, Drop } from '../api/types';
import { AuthProvider } from '../features/auth/AuthProvider';

interface Options {
  path: string;
  route: string;
  extraRoutes?: { path: string; element: ReactElement }[];
}

/** Renders a page inside the real providers and a memory router. */
export function renderPage(element: ReactElement, { path, route, extraRoutes = [] }: Options) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const router = createMemoryRouter([{ path, element }, ...extraRoutes], { initialEntries: [route] });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return { ...view, router };
}

export function signIn(role: 'USER' | 'ADMIN' = 'USER') {
  const session = {
    token: 'test-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 7, email: 'shopper@example.com', displayName: 'Demo Shopper', role },
  };
  window.localStorage.setItem('fluffle.session', JSON.stringify(session));
}

export function authResponse(): AuthResponse {
  return {
    accessToken: 'test-token',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 7, email: 'shopper@fluffle.dev', displayName: 'Demo Shopper', role: 'USER' },
  };
}

export function testDrop(overrides: Partial<Drop> = {}): Drop {
  const now = Date.now();
  return {
    id: 1,
    title: 'Forest Friends: Mochi Moss Launch',
    product: {
      id: 1, sku: 'FF-MOSS-001', name: 'Mochi Moss', series: 'Forest Friends', type: 'BLIND_BOX',
      description: 'A round, sleepy forest sprite.', imageUrl: '/images/products/mochi-moss.png', retailPrice: 14.99,
    },
    dropPrice: 11.99,
    originalPrice: 14.99,
    startTime: new Date(now - 60_000).toISOString(),
    endTime: new Date(now + 3_600_000).toISOString(),
    totalStock: 100,
    availableStock: 37,
    phase: 'LIVE',
    soldOut: false,
    ...overrides,
  };
}
