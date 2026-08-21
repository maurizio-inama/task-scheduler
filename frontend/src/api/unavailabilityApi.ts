import { request } from './client';
import type { Unavailability, UnavailabilityInput } from '../types/api';

export const unavailabilityApi = {
  list(): Promise<Unavailability[]> {
    return request<Unavailability[]>('/unavailability');
  },

  getById(id: number): Promise<Unavailability> {
    return request<Unavailability>(`/unavailability/${id}`);
  },

  create(input: UnavailabilityInput): Promise<Unavailability> {
    return request<Unavailability>('/unavailability', {
      method: 'POST',
      body: input,
    });
  },

  update(
    id: number,
    input: UnavailabilityInput,
  ): Promise<Unavailability> {
    return request<Unavailability>(`/unavailability/${id}`, {
      method: 'PUT',
      body: input,
    });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/unavailability/${id}`, { method: 'DELETE' });
  },
};
