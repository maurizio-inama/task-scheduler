import { request } from './client';
import type { Availability, AvailabilityInput } from '../types/api';

export const availabilityApi = {
  list(): Promise<Availability[]> {
    return request<Availability[]>('/availability');
  },

  getById(id: number): Promise<Availability> {
    return request<Availability>(`/availability/${id}`);
  },

  create(input: AvailabilityInput): Promise<Availability> {
    return request<Availability>('/availability', {
      method: 'POST',
      body: input,
    });
  },

  update(id: number, input: AvailabilityInput): Promise<Availability> {
    return request<Availability>(`/availability/${id}`, {
      method: 'PUT',
      body: input,
    });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/availability/${id}`, { method: 'DELETE' });
  },
};
