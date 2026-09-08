import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import TaskTimeline from './TaskTimeline';
import type { Assignment, Task, User } from '../types/api';

const listTasksMock = vi.fn();
const listAssignmentsMock = vi.fn();
const listUsersMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/tasksApi', () => ({
  tasksApi: { list: () => listTasksMock() },
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
    id: 10,
    title: 'Database backup production',
    description: 'Backup',
    status: 'SCHEDULED',
    priority: 'HIGH',
    estimatedDurationMinutes: 120,
    deadline: '2026-12-31',
    createdAt: '2026-01-01T08:00:00',
  },
  {
    id: 11,
    title: 'API test',
    description: 'Test',
    status: 'SCHEDULED',
    priority: 'MEDIUM',
    estimatedDurationMinutes: 60,
    deadline: '2026-12-31',
    createdAt: '2026-01-01T08:00:00',
  },
  {
    id: 12,
    title: 'Fix UI',
    description: 'Fix',
    status: 'SCHEDULED',
    priority: 'LOW',
    estimatedDurationMinutes: 30,
    deadline: null,
    createdAt: '2026-01-01T08:00:00',
  },
];

const USERS: User[] = [
  { id: 1, username: 'alice', firstName: 'Alice', lastName: 'A', email: 'a@x.io', role: 'OPERATOR', enabled: true, createdAt: '2026-01-01T08:00:00', updatedAt: '2026-01-01T08:00:00' },
  { id: 2, username: 'bob', firstName: 'Bob', lastName: 'B', email: 'b@x.io', role: 'OPERATOR', enabled: true, createdAt: '2026-01-01T08:00:00', updatedAt: '2026-01-01T08:00:00' },
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
    id: 100,
    userId: 1,
    taskId: 10,
    scheduleId: 1,
    status: 'ASSIGNED',
    startDateTime: dateTimeStr(2),
    endDateTime: dateTimeStr(4, '17:00:00'),
  },
  {
    id: 101,
    userId: 2,
    taskId: 11,
    scheduleId: 1,
    status: 'ASSIGNED',
    startDateTime: dateTimeStr(2, '09:00:00'),
    endDateTime: dateTimeStr(2, '11:00:00'),
  },
  {
    id: 102,
    userId: 1,
    taskId: 12,
    scheduleId: 1,
    status: 'ASSIGNED',
    startDateTime: dateTimeStr(2, '13:00:00'),
    endDateTime: dateTimeStr(2, '14:00:00'),
  },
];

function seedAdminAuth(): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'admin', role: 'ADMIN' }),
  );
}

describe('TaskTimeline', () => {
  beforeEach(() => {
    localStorage.clear();
    listTasksMock.mockReset();
    listAssignmentsMock.mockReset();
    listUsersMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' });
  });

  it('shows a loading state while keeping the calendar visible', () => {
    listTasksMock.mockReturnValue(new Promise(() => {}));
    listAssignmentsMock.mockReturnValue(new Promise(() => {}));

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    expect(screen.getByText('Loading timeline data…')).toBeTruthy();
    expect(document.querySelector('.loading .spinner')).toBeTruthy();
    expect(document.querySelectorAll('.week-row').length).toBeGreaterThan(0);
    expect(screen.queryByText(/No tasks scheduled/)).toBeNull();
  });

  it('renders seven day columns and every week row', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    const headers = screen.getAllByText(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$/);
    expect(headers).toHaveLength(7);

    const weekRows = document.querySelectorAll('.week-row');
    expect(weekRows.length).toBeGreaterThanOrEqual(1);
    weekRows.forEach((row) => {
      expect(row.querySelectorAll('.day-cell')).toHaveLength(7);
    });
  });

  it('shows an explicit Today badge on the current day', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    const todayCell = document.querySelector('.day-cell--today');
    expect(todayCell).toBeTruthy();
    expect(todayCell?.querySelector('.today-badge')?.textContent).toBe('Today');
  });

  it('provides day-of-month, month context and a Today badge in day cells', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    const todayNumber = document.querySelector(
      '.day-cell--today .day-number',
    )?.textContent;
    expect(Number(todayNumber)).toBeGreaterThanOrEqual(1);
    expect(Number(todayNumber)).toBeLessThanOrEqual(31);
    expect(document.querySelector('.day-cell--today .today-badge')).toBeTruthy();
    expect(document.querySelectorAll('.day-month').length).toBeGreaterThan(0);
  });

  it('shows a multi-day task in each day of its interval and multiple tasks in one day', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    await waitFor(() =>
      expect(
        screen.getAllByTitle('Database backup production — alice'),
      ).toHaveLength(3),
    );

    expect(screen.getByTitle('API test — bob')).toBeTruthy();
    expect(screen.getByTitle('Fix UI — alice')).toBeTruthy();
    expect(document.querySelectorAll('.task-card')).toHaveLength(5);
  });

  it('keeps resource colors stable for the same user across the grid', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    await waitFor(() =>
      expect(
        document.querySelectorAll('.task-card[title*="alice"]').length,
      ).toBeGreaterThan(0),
    );

    const cards = [...document.querySelectorAll<HTMLElement>('.task-card')];
    const aliceCards = cards.filter(
      (c) => c.getAttribute('title')?.includes('alice'),
    );
    const bobCards = cards.filter(
      (c) => c.getAttribute('title')?.includes('bob'),
    );

    expect(aliceCards.length).toBeGreaterThan(0);
    expect(bobCards.length).toBeGreaterThan(0);

    const aliceBg = new Set(aliceCards.map((c) => c.style.backgroundColor));
    const bobBg = new Set(bobCards.map((c) => c.style.backgroundColor));
    expect(aliceBg.size).toBe(1);
    expect(bobBg.size).toBe(1);
    expect([...aliceBg][0]).not.toBe([...bobBg][0]);
  });

  it('gives a multi-day task the same border color in every cell', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    await waitFor(() =>
      expect(
        screen.getAllByTitle('Database backup production — alice'),
      ).toHaveLength(3),
    );

    const spans = document.querySelectorAll(
      '.task-card[title="Database backup production — alice"]',
    );
    expect(spans.length).toBe(3);
    const borders = new Set(
      [...spans].map((c) => (c as HTMLElement).style.borderLeftColor),
    );
    expect(borders.size).toBe(1);
  });

  it('renders an empty calendar when there are no assignments', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue([]);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    expect(document.querySelectorAll('.task-card')).toHaveLength(0);
    expect(document.querySelectorAll('.day-cell').length).toBeGreaterThan(0);
  });

  it('shows a non-blocking error state without hiding the calendar', async () => {
    listTasksMock.mockRejectedValue(new Error('boom'));
    listAssignmentsMock.mockRejectedValue(new Error('boom'));

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() =>
      screen.getByText('Unable to load timeline data.'),
    );
    expect(document.querySelector('.timeline-error')).toBeTruthy();
    expect(document.querySelectorAll('.week-row').length).toBeGreaterThan(0);
    expect(screen.queryByText(/No tasks scheduled/)).toBeNull();
  });

  it('retries the failing requests when Retry is pressed', async () => {
    listTasksMock.mockRejectedValue(new Error('boom'));
    listAssignmentsMock.mockRejectedValue(new Error('boom'));

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Unable to load timeline data.'));
    const callsBefore = listTasksMock.mock.calls.length;

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() =>
      expect(listTasksMock.mock.calls.length).toBeGreaterThan(callsBefore),
    );
    await waitFor(() => screen.getByText('Unable to load timeline data.'));
  });

  it('renders a legend that mirrors the TaskCard fill and border colors', async () => {
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    const legend = screen.getByLabelText('Timeline legend');
    await waitFor(() => expect(legend.textContent).toContain('alice'));

    expect(legend.textContent).toContain('Resource');
    expect(legend.textContent).toContain('Task');
    expect(legend.textContent).toContain('Resources');
    expect(legend.textContent).toContain('Tasks');

    const aliceEntry = [...legend.querySelectorAll('.legend-entry')].find(
      (el) => el.textContent?.includes('alice'),
    );
    const aliceFill = aliceEntry?.querySelector('.legend-swatch--fill') as HTMLElement;
    const aliceCard = document.querySelector('.task-card[title*="alice"]') as HTMLElement;
    expect(aliceFill.style.backgroundColor).toBe(aliceCard.style.backgroundColor);

    const backupEntry = [...legend.querySelectorAll('.legend-entry')].find(
      (el) => el.textContent?.includes('backup'),
    );
    const backupBorder = backupEntry?.querySelector('.legend-swatch--border') as HTMLElement;
    const backupCard = document.querySelector(
      '.task-card[title*="Database backup production"]',
    ) as HTMLElement;
    expect(backupBorder.style.borderColor).toBe(backupCard.style.borderLeftColor);
  });
});

describe('TaskTimeline temporal navigation', () => {
  const FIXED_NOW = new Date(2026, 8, 9, 12, 0, 0);

  beforeEach(() => {
    localStorage.clear();
    listTasksMock.mockReset();
    listAssignmentsMock.mockReset();
    listUsersMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' });
    seedAdminAuth();
    listTasksMock.mockResolvedValue(TASKS);
    listAssignmentsMock.mockResolvedValue(ASSIGNMENTS);
    listUsersMock.mockResolvedValue(USERS);

    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders previous/today/next controls with accessible names and the current week period', async () => {
    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    expect(screen.getByRole('button', { name: 'Previous week' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Go to current week' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Next week' })).toBeTruthy();
    expect(screen.getByText('September 7 – 13, 2026')).toBeTruthy();
  });

  it('advances exactly one week when Next is pressed', async () => {
    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));

    expect(screen.getByText('September 14 – 20, 2026')).toBeTruthy();
    expect(
      document.querySelector('.week-row .day-cell .day-number')?.textContent,
    ).toBe('14');
  });

  it('moves back exactly one week when Previous is pressed', async () => {
    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    fireEvent.click(screen.getByRole('button', { name: 'Previous week' }));

    expect(screen.getByText('August 31 – September 6, 2026')).toBeTruthy();
    expect(
      document.querySelector('.week-row .day-cell .day-number')?.textContent,
    ).toBe('31');
  });

  it('restores the current week when Today is pressed after navigating', async () => {
    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));
    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));
    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));

    expect(screen.getByText('September 21 – 27, 2026')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Go to current week' }));

    expect(screen.getByText('September 7 – 13, 2026')).toBeTruthy();
  });

  it('crosses a year boundary continuously, one week per action', async () => {
    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    for (let i = 0; i < 16; i++) {
      fireEvent.click(screen.getByRole('button', { name: 'Next week' }));
    }

    expect(
      screen.getByText('December 28, 2026 – January 3, 2027'),
    ).toBeTruthy();
  });

  it('shows an empty message for a week with no tasks while navigation stays usable', async () => {
    listAssignmentsMock.mockResolvedValue([
      {
        id: 200,
        userId: 1,
        taskId: 10,
        scheduleId: 1,
        status: 'ASSIGNED',
        startDateTime: dateTimeStr(14),
        endDateTime: dateTimeStr(16, '17:00:00'),
      },
    ]);

    render(
      <AuthProvider>
        <TaskTimeline />
      </AuthProvider>,
    );

    await waitFor(() => screen.getByText('Mon'));

    expect(screen.getByText(/No tasks scheduled for this week\./)).toBeTruthy();
    expect(document.querySelectorAll('.week-row').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));
    expect(screen.getByText(/No tasks scheduled for this week\./)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Next week' }));
    expect(screen.queryByText(/No tasks scheduled for this week\./)).toBeNull();
    await waitFor(() =>
      expect(
        screen.getAllByTitle('Database backup production — alice').length,
      ).toBeGreaterThan(0),
    );
  });
});