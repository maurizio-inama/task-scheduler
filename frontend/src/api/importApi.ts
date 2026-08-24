import { request } from './client';
import type {
  ImportResult,
  ScenarioSummary,
  ValidationReport,
} from '../types/api';

function upload(path: string, file: File): Promise<unknown> {
  const formData = new FormData();
  formData.append('file', file);
  return request<unknown>(path, { method: 'POST', body: formData });
}

export const importApi = {
  validateFile(file: File): Promise<ValidationReport> {
    return upload('/admin/import/validate', file) as Promise<ValidationReport>;
  },

  importFile(file: File): Promise<ImportResult> {
    return upload('/admin/import', file) as Promise<ImportResult>;
  },

  listScenarios(): Promise<ScenarioSummary[]> {
    return request<ScenarioSummary[]>('/admin/import/scenarios');
  },

  validateScenario(fileName: string): Promise<ValidationReport> {
    return request<ValidationReport>(
      `/admin/import/scenarios/${encodeURIComponent(fileName)}/validate`,
      { method: 'POST' },
    );
  },

  importScenario(fileName: string): Promise<ImportResult> {
    return request<ImportResult>(
      `/admin/import/scenarios/${encodeURIComponent(fileName)}/import`,
      { method: 'POST' },
    );
  },
};
