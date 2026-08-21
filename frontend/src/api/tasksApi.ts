import { request } from './client';
import type { Task, TaskInput } from '../types/api';

export const tasksApi = {
  list(): Promise<Task[]> {
    return request<Task[]>('/tasks');
  },

  getById(id: number): Promise<Task> {
    return request<Task>(`/tasks/${id}`);
  },

  create(input: TaskInput): Promise<Task> {
    return request<Task>('/tasks', { method: 'POST', body: input });
  },

  update(id: number, input: TaskInput): Promise<Task> {
    return request<Task>(`/tasks/${id}`, { method: 'PUT', body: input });
  },

  remove(id: number): Promise<void> {
    return request<void>(`/tasks/${id}`, { method: 'DELETE' });
  },
};
