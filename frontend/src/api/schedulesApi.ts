import { request } from './client';
import type { GenerateResponse, Schedule, ScheduleInput } from '../types/api';

export const schedulesApi = {
  list(): Promise<Schedule[]> {
    return request<Schedule[]>('/schedules');
  },

  getById(id: number): Promise<Schedule> {
    return request<Schedule>(`/schedules/${id}`);
  },

  create(input: ScheduleInput): Promise<Schedule> {
    return request<Schedule>('/schedules', { method: 'POST', body: input });
  },

  update(id: number, input: ScheduleInput): Promise<Schedule> {
    return request<Schedule>(`/schedules/${id}`, { method: 'PUT', body: input });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/schedules/${id}`, { method: 'DELETE' });
  },

  generate(id: number): Promise<GenerateResponse> {
    return request<GenerateResponse>(`/schedules/${id}/generate`, {
      method: 'POST',
    });
  },
};
