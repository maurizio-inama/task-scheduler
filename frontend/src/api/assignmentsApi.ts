import { request } from './client';
import type { Assignment, AssignmentInput } from '../types/api';

export const assignmentsApi = {
  list(): Promise<Assignment[]> {
    return request<Assignment[]>('/assignments');
  },

  getById(id: number): Promise<Assignment> {
    return request<Assignment>(`/assignments/${id}`);
  },

  create(input: AssignmentInput): Promise<Assignment> {
    return request<Assignment>('/assignments', { method: 'POST', body: input });
  },

  update(
    id: number,
    input: AssignmentInput,
  ): Promise<Assignment> {
    return request<Assignment>(`/assignments/${id}`, {
      method: 'PUT',
      body: input,
    });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/assignments/${id}`, { method: 'DELETE' });
  },
};
