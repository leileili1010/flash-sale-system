import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureAuth } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import type { AuthResponse, User } from '../../api/types';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (email: string, password: string, displayName: string) => Promise<User>;
  logout: () => void;
}

const STORAGE_KEY = 'fluffle.session';
const AuthContext = createContext<AuthContextValue | null>(null);

function loadSession(): Session | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage can be unavailable (private mode); the session then lasts for this tab only.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(loadSession);

  const logout = useCallback(() => {
    saveSession(null);
    setSession(null);
    queryClient.removeQueries({ queryKey: ['orders'] });
  }, [queryClient]);

  // Read the token lazily so requests always use the latest session.
  useEffect(() => {
    configureAuth(() => loadSession()?.token ?? null, logout);
  }, [logout]);

  const start = useCallback((response: AuthResponse) => {
    const next = { token: response.accessToken, expiresAt: response.expiresAt, user: response.user };
    saveSession(next);
    setSession(next);
    return response.user;
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user: session?.user ?? null,
    isAuthenticated: session !== null,
    login: async (email, password) => start(await endpoints.login(email, password)),
    register: async (email, password, displayName) => start(await endpoints.register(email, password, displayName)),
    logout,
  }), [session, start, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
