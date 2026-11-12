import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from './AuthProvider';

/** Sends signed-out visitors to the sign-in page and brings them back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return children;
}

/** Like RequireAuth, but also requires the ADMIN role. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user && user.role !== 'ADMIN') {
    return (
      <section className="mx-auto mt-8 max-w-xl rounded-[30px] bg-white p-10 text-center shadow-card">
        <h1 className="font-display text-3xl font-bold">Admins only</h1>
        <p className="mt-2 text-muted">Your account doesn't have access to the admin console.</p>
      </section>
    );
  }
  return <RequireAuth>{children}</RequireAuth>;
}

/** Only allows same-site relative paths as a post-login destination. */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}
