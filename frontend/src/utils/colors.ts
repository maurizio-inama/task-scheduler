const RESOURCE_PALETTE = [
  { bg: '#dbeafe', border: '#93c5fd' },
  { bg: '#dcfce7', border: '#86efac' },
  { bg: '#fef3c7', border: '#fcd34d' },
  { bg: '#fce7f3', border: '#f9a8d4' },
  { bg: '#e0e7ff', border: '#a5b4fc' },
  { bg: '#ccfbf1', border: '#5eead4' },
  { bg: '#fee2e2', border: '#fca5a5' },
  { bg: '#f3e8ff', border: '#c084fc' },
  { bg: '#ecfdf5', border: '#6ee7b7' },
  { bg: '#fff7ed', border: '#fdba74' },
];

const TASK_PALETTE = [
  '#3b82f6',
  '#22c55e',
  '#f59e0b',
  '#ec4899',
  '#6366f1',
  '#14b8a6',
  '#ef4444',
  '#a855f7',
  '#10b981',
  '#f97316',
];

export function getResourceColors(userId: number): { bg: string; border: string } {
  return RESOURCE_PALETTE[Math.abs(userId) % RESOURCE_PALETTE.length];
}

export function getTaskBorderColor(taskId: number): string {
  return TASK_PALETTE[Math.abs(taskId) % TASK_PALETTE.length];
}
