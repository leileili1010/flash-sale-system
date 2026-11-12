import { Outlet, useNavigate } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { CloudBand } from './CloudBand';
import { Header } from './Header';

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen flex-col overflow-x-hidden">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-full focus:bg-white focus:px-4 focus:py-2">
        Skip to content
      </a>
      <Header
        user={user ? { displayName: user.displayName, isAdmin: user.role === 'ADMIN' } : null}
        onSignOut={() => {
          logout();
          void navigate('/');
        }}
      />
      <main id="main" className="mx-auto w-full max-w-[1240px] flex-1 px-6 pt-3">
        <Outlet />
      </main>
      <CloudBand />
    </div>
  );
}
