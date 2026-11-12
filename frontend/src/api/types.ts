/** Shapes returned by the backend REST API. Money arrives as JSON numbers, timestamps as ISO-8601 strings. */

export type ProductType = 'BLIND_BOX' | 'FIGURE' | 'PLUSH';
export type DropPhase = 'UPCOMING' | 'LIVE' | 'ENDED';
export type OrderStatus = 'QUEUED' | 'PENDING_PAYMENT' | 'PAID' | 'CLOSED' | 'FAILED';
export type Role = 'USER' | 'ADMIN';

export interface Product {
  id: number;
  sku: string;
  name: string;
  series: string;
  type: ProductType;
  description: string;
  imageUrl: string;
  retailPrice: number;
}

export interface Drop {
  id: number;
  title: string;
  product: Product;
  dropPrice: number;
  originalPrice: number;
  startTime: string;
  endTime: string;
  totalStock: number;
  availableStock: number;
  phase: DropPhase;
  soldOut: boolean;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: User;
}

export interface PurchaseAccepted {
  /** 64-bit order numbers exceed JavaScript's safe integer range, so they are strings. */
  orderNo: string;
  dropId: number;
  status: 'QUEUED';
}

export interface Order {
  orderNo: string;
  dropId: number;
  status: OrderStatus;
  amount: number | null;
  failureReason: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  paidAt: string | null;
}

export interface ServerTime {
  serverTime: string;
  epochMillis: number;
}

/** Stable error codes from the backend's RFC 9457 problem responses. */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'INVALID_CREDENTIALS'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'EMAIL_TAKEN'
  | 'DROP_NOT_STARTED'
  | 'DROP_ENDED'
  | 'SOLD_OUT'
  | 'ALREADY_PURCHASED'
  | 'ORDER_EXPIRED'
  | 'INVALID_ORDER_STATE'
  | 'RATE_LIMITED'
  | 'SERVICE_UNAVAILABLE'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR';

export interface Problem {
  title?: string;
  status?: number;
  detail?: string;
  code?: ErrorCode;
  errors?: Record<string, string>;
}

export type DropStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED';

/** A drop as seen in the admin console, with its full stock breakdown. */
export interface AdminDrop {
  id: number;
  title: string;
  productId: number;
  productName: string;
  imageUrl: string;
  status: DropStatus;
  phase: DropPhase;
  startTime: string;
  endTime: string;
  dropPrice: number;
  originalPrice: number;
  totalStock: number;
  availableStock: number;
  lockedStock: number;
  soldStock: number;
}

export interface CreateDropInput {
  productId: number;
  title: string;
  dropPrice: number;
  startTime: string;
  endTime: string;
  totalStock: number;
  publish: boolean;
}

/** Redis vs. database stock for one active drop. `drift` is 0 when consistent, null when not loaded. */
export interface StockReport {
  dropId: number;
  title: string;
  totalStock: number;
  databaseAvailable: number;
  databaseLocked: number;
  databaseSold: number;
  redisStock: number | null;
  redisBuyers: number;
  liveOrders: number;
  inFlightReservations: number;
  drift: number | null;
}
