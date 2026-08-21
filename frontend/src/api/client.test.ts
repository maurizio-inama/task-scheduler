import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AUTH_EXPIRED_EVENT,
  ApiRequestError,
  clearStoredAuth,
  loadStoredAuth,
  request,
  saveStoredAuth,
} from './client';

describe('auth storage helpers', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns null when nothing is stored', () => {
    expect(loadStoredAuth()).toBeNull();
  });

  it('round-trips a stored auth payload', () => {
    saveStoredAuth({ token: 't-1', username: 'admin', role: 'ADMIN' });
    expect(loadStoredAuth()).toEqual({
      token: 't-1',
      username: 'admin',
      role: 'ADMIN',
    });
    clearStoredAuth();
    expect(loadStoredAuth()).toBeNull();
  });
});

describe('request', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function okResponse(body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('sends the bearer token and JSON body for authenticated requests', async () => {
    saveStoredAuth({ token: 'jwt-1', username: 'op', role: 'OPERATOR' });
    fetchMock.mockResolvedValue(okResponse({ ok: true }));

    await request('/tasks', { method: 'POST', body: { title: 'x' } });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/tasks');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer jwt-1');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe(JSON.stringify({ title: 'x' }));
  });

  it('omits the Authorization header when authenticated is false', async () => {
    saveStoredAuth({ token: 'jwt-1', username: 'op', role: 'OPERATOR' });
    fetchMock.mockResolvedValue(okResponse({ token: 'abc' }));

    await request('/auth/login', {
      method: 'POST',
      body: {},
      authenticated: false,
    });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
  });

  it('maps an API error body onto ApiRequestError', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 409,
          error: 'BUSINESS_RULE_VIOLATION',
          message: 'Schedule overlaps another schedule.',
          path: '/api/schedules',
          timestamp: '2026-08-21T10:00:00Z',
        }),
        { status: 409 },
      ),
    );

    const promise = request('/schedules');
    await expect(promise).rejects.toMatchObject({
      status: 409,
      code: 'BUSINESS_RULE_VIOLATION',
      message: 'Schedule overlaps another schedule.',
    });
  });

  it('falls back to generic messages for non-JSON errors', async () => {
    fetchMock.mockResolvedValue(new Response('boom', { status: 500 }));

    const promise = request('/tasks');
    await expect(promise).rejects.toMatchObject({
      status: 500,
      code: 'SERVER_ERROR',
      message: 'The server encountered an unexpected error.',
    });
  });

  it('clears stored auth and broadcasts an event on 401', async () => {
    saveStoredAuth({ token: 'stale', username: 'op', role: 'OPERATOR' });
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 401,
          error: 'UNAUTHORIZED',
          message: 'Token expired',
          path: '/api/tasks',
          timestamp: '2026-08-21T10:00:00Z',
        }),
        { status: 401 },
      ),
    );

    const listener = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, listener);

    const promise = request('/tasks');
    await expect(promise).rejects.toBeInstanceOf(ApiRequestError);

    expect(loadStoredAuth()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener);
  });

  it('wraps network failures in a NETWORK_ERROR', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const promise = request('/tasks');
    await expect(promise).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    });
  });

  it('resolves undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(request('/tasks/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
