import type { ErrorCode, Problem } from './types';

/** An API failure with the backend's machine-readable code, so the UI can react to each case. */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  /** Seconds until a rate-limited request may be retried, from the Retry-After header. */
  readonly retryAfterSeconds: number | null;

  constructor(code: ErrorCode, status: number, message: string,
              fieldErrors: Record<string, string> = {}, retryAfterSeconds: number | null = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

type TokenProvider = () => string | null;
type UnauthorizedListener = () => void;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: UnauthorizedListener = () => {};

/** Wires the client to the auth state without the client depending on React. */
export function configureAuth(provider: TokenProvider, unauthorized: UnauthorizedListener) {
  tokenProvider = provider;
  onUnauthorized = unauthorized;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenProvider();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError('NETWORK_ERROR', 0, 'Could not reach the server. Check your connection and try again.');
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T;
  }

  const problem = await readProblem(response);
  if (response.status === 401 && token) {
    onUnauthorized();
  }
  const retryAfter = response.headers.get('Retry-After');
  throw new ApiError(
    problem.code ?? fallbackCode(response.status),
    response.status,
    problem.detail ?? problem.title ?? `The server returned an unexpected response (HTTP ${response.status}).`,
    problem.errors ?? {},
    retryAfter === null ? null : Number(retryAfter),
  );
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    return (await response.json()) as Problem;
  } catch {
    return {};
  }
}

function fallbackCode(status: number): ErrorCode {
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  if (status === 503) return 'SERVICE_UNAVAILABLE';
  return status >= 500 ? 'INTERNAL_ERROR' : 'VALIDATION_FAILED';
}
