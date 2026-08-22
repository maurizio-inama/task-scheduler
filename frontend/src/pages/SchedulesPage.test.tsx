import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { SchedulesPage } from './SchedulesPage';

const listSchedulesMock = vi.fn();
const createScheduleMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/schedulesApi', () => ({
  schedulesApi: {
    list: () => listSchedulesMock(),
    getById: vi.fn(),
    create: (...args: unknown[]) => createScheduleMock(...args),
    update: vi.fn(),
    remove: vi.fn(),
    generate: vi.fn(),
  },
}));

vi.mock('../api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    me: () => meMock(),
  },
}));

const SAMPLE_SCHEDULE = {
  id: 2,
  startDateTime: '2026-09-07T08:00:00',
  endDateTime: '2026-09-07T18:00:00',
  createdAt: '2026-08-20T10:00:00',
};

function seedAuth(role: string): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'user', role }),
  );
}

describe('SchedulesPage', () => {
  beforeEach(() => {
    localStorage.clear();
    listSchedulesMock.mockReset();
    createScheduleMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 1, username: 'user', role: 'ADMIN' });
    listSchedulesMock.mockResolvedValue([SAMPLE_SCHEDULE]);
  });

  it('creates a schedule with date-only inputs as a full-day window', async () => {
    seedAuth('ADMIN');
    createScheduleMock.mockResolvedValue(SAMPLE_SCHEDULE);
    localStorage.setItem(
      'task-scheduler-auth',
      JSON.stringify({ token: 'jwt', username: 'user', role: 'ADMIN' }),
    );
    render(
      <AuthProvider>
        <SchedulesPage />
      </AuthProvider>,
    );
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(
      screen.getByRole('button', { name: /new schedule/i }),
    );
    await userEvent.type(screen.getByLabelText(/^from \*/i), '2026-09-28');
    await userEvent.type(screen.getByLabelText(/^to \*/i), '2026-09-28');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));

    await waitFor(() => {
      expect(createScheduleMock).toHaveBeenCalledTimes(1);
    });
    expect(createScheduleMock.mock.calls[0][0]).toMatchObject({
      startDateTime: '2026-09-28T00:00',
      endDateTime: '2026-09-28T23:59',
      status: 'DRAFT',
    });
  });

  it('keeps explicit times when both date and time are given', async () => {
    seedAuth('ADMIN');
    createScheduleMock.mockResolvedValue(SAMPLE_SCHEDULE);
    render(
      <AuthProvider>
        <SchedulesPage />
      </AuthProvider>,
    );
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(
      screen.getByRole('button', { name: /new schedule/i }),
    );
    await userEvent.type(screen.getByLabelText(/^from \*/i), '2026-09-28');
    await userEvent.type(screen.getByLabelText('From time'), '08:30');
    await userEvent.type(screen.getByLabelText(/^to \*/i), '2026-09-28');
    await userEvent.type(screen.getByLabelText('To time'), '13:00');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));

    await waitFor(() => {
      expect(createScheduleMock).toHaveBeenCalledTimes(1);
    });
    expect(createScheduleMock.mock.calls[0][0]).toMatchObject({
      startDateTime: '2026-09-28T08:30',
      endDateTime: '2026-09-28T13:00',
    });
  });

  it('rejects an end date that precedes the start date across defaults', async () => {
    seedAuth('ADMIN');
    render(
      <AuthProvider>
        <SchedulesPage />
      </AuthProvider>,
    );
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(
      screen.getByRole('button', { name: /new schedule/i }),
    );
    await userEvent.type(screen.getByLabelText(/^from \*/i), '2026-09-29');
    await userEvent.type(screen.getByLabelText(/^to \*/i), '2026-09-28');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));

    expect(
      await screen.findByText('The end must be after the start.'),
    ).toBeInTheDocument();
    expect(createScheduleMock).not.toHaveBeenCalled();
  });
});
