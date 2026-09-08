import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { DashboardPage } from './DashboardPage';
import type { Assignment, Task, User } from '../types/api';

const listTasksMock = vi.fn();
const listSchedulesMock = vi.fn();
const listAssignmentsMock = vi.fn();
const listUsersMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/tasksApi', () => ({
  tasksApi: { list: () => listTasksMock() },
}));
vi.mock('../api/schedulesApi', () => ({
  schedulesApi: { list: () => listSchedulesMock() },
}));
vi.mock('../api/assignmentsApi', () => ({
  assignmentsApi: { list: () => listAssignmentsMock() },
}));
vi.mock('../api/usersApi', () => ({
  usersApi: { list: () => listUsersMock() },
}));
vi.mock('../api/authApi', () => ({
  authApi: { login: vi.fn(), me: () => meMock() },
}));

const TASKS: Task[] = [
  {
    id: 1,
    title: 'Database backup production',
    description: '',
    status: 'PENDING',
    priority: 'HIGH',
    estimatedDurationMinutes: 120,
    deadline: null,
    createdAt: '2026-01-01T08:00:00',
  },
  {
    id: 2,
    title: 'API test',
    description: '',
    status: 'SCHEDULED',
    priority: 'MEDIUM',
    estimatedDurationMinutes: 60,
    deadline: null,
    createdAt: '2026-01-01T08:00:00',
  },
];

const SCHEDULES = [
  {
    id: 1,
    startDateTime: '2026-09-07T08:00:00',
    endDateTime: '2026-09-07T12:00:00',
    createdAt: '2026-09-01T08:00:00',
  },
];

const USERS: User[] = [
  { id: 1, username: 'alice', firstName: 'Alice', lastName: 'A', email: 'a@x.io', role: 'OPERATOR', enabled: true, createdAt: '2026-01-01T08:00:00', updatedAt: '2026-01-01T08:00:00' },
];

function dateTimeStr(offsetDays: number, time = '08:00:00'): string {
  const now = new Date();
  const diff = (now.getDay() + 6) % 7;
  const d = new Date(now);
  d.setDate(d.getDate() - diff + offsetDays);
  d.setHours(0, 0, 0, 0);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}T${time}`;
}

const ASSIGNMENTS: Assignment[] = [
  {
    id: 10,
    userId: 1,
    taskId: 1,
    scheduleId: 1,
    status: 'ASSIGNED',
    startDateTime: dateTimeStr(2),
    endDateTime: dateTimeStr(4, '17:00:00'),
  },
];

function seedAdminAuth(): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'admin', role: 'ADMIN' }),
  );
}

function renderDashboard() {
  return render(
    <AuthProvider>
      <DashboardPage />
    </AuthProvider>,
  );
}

describe('DashboardPage with TaskTimeline', () => {
  beforeEach(() => {
    localStorage.clear();
    listTasksMock.mockReset();
    listSchedulesMock.mockReset();
    listAssignmentsMock.mockReset();
    listUsersMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' });
    seedAdminAuth();
  });

  it('renders the existing Dashboard sections together with an empty timeline', async () => {
    listTasksMock.mockResolvedValue(TASKS);
    listSchedulesMock.mockResolvedValue(SCHEDULES);
    listAssignmentsMock.mockResolvedValue([]);
    listUsersMock.mockResolvedValue(USERS);

    renderDashboard();

    await waitFor(() => screen.getByText('Tasks'));
    await waitFor(() => screen.getByText(/No tasks scheduled for this week\./));

    expect(screen.getByRole('heading', { name: 'Task Timeline' })).toBeTruthy();
    expect(document.querySelectorAll('.week-row .day-cell')).toHaveLength(7);

    expect(screen.getByRole('heading', { name: 'Tasks by status' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Upcoming schedule windows' })).toBeTruthy();
    expect(screen.getAllByText('#1')).toHaveLength(1);
  });

  it('renders timeline task cards and the legend inside the Dashboard', async () => {
    listTasksMock.mockResolvedValue(TASKS);
    listSchedulesMock.mockResolvedValue(SCHEDULES);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    renderDashboard();

    await waitFor(() =>
      expect(
        screen.getAllByTitle('Database backup production — alice').length,
      ).toBeGreaterThan(0),
    );

    expect(screen.getByLabelText('Timeline legend')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Previous week' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Next week' })).toBeTruthy();

    const statGrid = document.querySelector('.stat-grid');
    expect(statGrid?.textContent).toContain('2');
    expect(statGrid?.textContent).toContain('Schedules');
  });
});