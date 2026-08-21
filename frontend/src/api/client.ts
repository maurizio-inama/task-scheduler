import type { ApiErrorBody } from '../types/api';

export const BASE_URL: string = import.meta.env.VITE_API_URL ?? '/api';

const AUTH_STORAGE_KEY = 'task-scheduler-auth';

export const AUTH_EXPIRED_EVENT = 'task-scheduler:auth-expired';

export interface StoredAuth {
  token: string;
  username: string;
  role: string;
}

export function loadStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredAuth) : null;
  } catch {
    return null;
  }
}

export function saveStoredAuth(auth: StoredAuth): void {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
}

export function clearStoredAuth(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  authenticated?: boolean;
}

export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = 'GET', body, authenticated = true } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (authenticated) {
    const auth = loadStoredAuth();
    if (auth?.token) {
      headers.Authorization = `Bearer ${auth.token}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiRequestError(
      0,
      'NETWORK_ERROR',
      'Unable to reach the server. Check your connection and try again.',
    );
  }

  if (response.status === 401 && authenticated) {
    clearStoredAuth();
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiRequestError> {
  const status = response.status;
  let code = fallbackCode(status);
  let message = fallbackMessage(status);

  try {
    const body = (await response.json()) as Partial<ApiErrorBody>;
    if (typeof body.message === 'string' && body.message.length > 0) {
      message = body.message;
    }
    if (typeof body.error === 'string' && body.error.length > 0) {
      code = body.error;
    }
  } catch {
    // non-JSON error body: keep the fallback message
  }

  return new ApiRequestError(status, code, message);
}

function fallbackCode(status: number): string {
  switch (status) {
    case 400:
      return 'BAD_REQUEST';
    case 401:
      return 'UNAUTHORIZED';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'CONFLICT';
    default:
      return status >= 500 ? 'SERVER_ERROR' : 'REQUEST_FAILED';
  }
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 400:
      return 'The request was invalid.';
    case 401:
      return 'Your session has expired. Please sign in again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'The request conflicts with the current state.';
    default:
      return status >= 500
        ? 'The server encountered an unexpected error.'
        : 'The request could not be completed.';
  }
}
