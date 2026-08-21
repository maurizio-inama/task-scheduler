import { request } from './client';
import type { User, UserInput } from '../types/api';

export const usersApi = {
  list(): Promise<User[]> {
    return request<User[]>('/users');
  },

  getById(id: number): Promise<User> {
    return request<User>(`/users/${id}`);
  },

  create(input: UserInput): Promise<User> {
    return request<User>('/users', { method: 'POST', body: input });
  },

  update(id: number, input: UserInput): Promise<User> {
    return request<User>(`/users/${id}`, { method: 'PUT', body: input });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/users/${id}`, { method: 'DELETE' });
  },
};
