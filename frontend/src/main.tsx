import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { ApiError } from './api/client';
import { Layout } from './components/Layout';
import { AuthProvider } from './features/auth/AuthProvider';
import './index.css';
import { ServerClockProvider } from './lib/serverClock';
import { HomePage } from './pages/HomePage';
import { NotFoundPage, PlaceholderPage } from './pages/PlaceholderPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5_000,
      // Business errors (404, 409, ...) will not succeed on retry; network blips might.
      retry: (failureCount, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && failureCount < 2,
    },
  },
});

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'drops/:dropId', element: <PlaceholderPage title="Drop details" /> },
      { path: 'orders', element: <PlaceholderPage title="My orders" /> },
      { path: 'login', element: <PlaceholderPage title="Sign in" /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ServerClockProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ServerClockProvider>
    </QueryClientProvider>
  </StrictMode>,
);
