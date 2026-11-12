import { api } from './client';
import type { AuthResponse, Drop, DropPhase, Order, Product, PurchaseAccepted, ServerTime, User } from './types';

export const endpoints = {
  serverTime: () => api<ServerTime>('/api/time'),

  products: () => api<Product[]>('/api/products'),
  drops: (phase?: DropPhase) => api<Drop[]>(phase ? `/api/drops?phase=${phase}` : '/api/drops'),
  drop: (id: number) => api<Drop>(`/api/drops/${id}`),

  login: (email: string, password: string) =>
    api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }),
  register: (email: string, password: string, displayName: string) =>
    api<AuthResponse>('/api/auth/register', { method: 'POST', body: { email, password, displayName } }),
  me: () => api<User>('/api/auth/me'),

  purchase: (dropId: number) => api<PurchaseAccepted>(`/api/drops/${dropId}/purchase`, { method: 'POST' }),
  order: (orderNo: string) => api<Order>(`/api/orders/${orderNo}`),
  orders: () => api<Order[]>('/api/orders'),
  pay: (orderNo: string) => api<Order>(`/api/orders/${orderNo}/pay`, { method: 'POST' }),
};
