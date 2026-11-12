import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, configureAuth } from './client';

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

describe('api client', () => {
  beforeEach(() => {
    configureAuth(() => null, () => {});
  });

  it('returns parsed JSON on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { epochMillis: 42 }));
    await expect(api('/api/time')).resolves.toEqual({ epochMillis: 42 });
  });

  it('sends the bearer token when signed in', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, {}));
    configureAuth(() => 'token-123', () => {});

    await api('/api/orders');

    const init = fetchSpy.mock.calls[0]![1]!;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-123');
  });

  it('turns problem responses into ApiErrors with the backend code', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(409, { status: 409, detail: 'This item is sold out.', code: 'SOLD_OUT' }));

    await expect(api('/api/drops/1/purchase', { method: 'POST' })).rejects.toMatchObject({
      code: 'SOLD_OUT',
      status: 409,
      message: 'This item is sold out.',
    });
  });

  it('exposes Retry-After for rate-limited requests', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(429, { code: 'RATE_LIMITED', detail: 'Too many requests.' }, { 'Retry-After': '3' }));

    const error = await api('/api/drops/1/purchase', { method: 'POST' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).retryAfterSeconds).toBe(3);
  });

  it('reports network failures distinctly', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(api('/api/drops')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
  });

  it('signs the user out when an authenticated request is rejected', async () => {
    const onUnauthorized = vi.fn();
    configureAuth(() => 'expired-token', onUnauthorized);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(401, { code: 'UNAUTHORIZED' }));

    await expect(api('/api/orders')).rejects.toMatchObject({ code: 'UNAUTHORIZED' });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });
});
