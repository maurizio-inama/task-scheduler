import { request } from './client';
import type { AuthResponse, MeResponse } from '../types/api';

export const authApi = {
  login(username: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: { username, password },
      authenticated: false,
    });
  },

  me(): Promise<MeResponse> {
    return request<MeResponse>('/auth/me');
  },
};
