import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { UnavailabilityPage } from './UnavailabilityPage';

const listUnavailabilityMock = vi.fn();
const createUnavailabilityMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/unavailabilityApi', () => ({
  unavailabilityApi: {
    list: () => listUnavailabilityMock(),
    getById: vi.fn(),
    create: (...args: unknown[]) => createUnavailabilityMock(...args),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('../api/usersApi', () => ({
  usersApi: {
    list: vi.fn(),
  },
}));

vi.mock('../api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    me: () => meMock(),
  },
}));

const SAMPLE_ENTRY = {
  id: 5,
  userId: 7,
  startDateTime: '2026-09-14T09:00:00',
  endDateTime: '2026-09-14T13:00:00',
  reason: 'Dentist',
};

function seedAuth(role: string): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'operator', role }),
  );
}

describe('UnavailabilityPage', () => {
  beforeEach(() => {
    localStorage.clear();
    listUnavailabilityMock.mockReset();
    createUnavailabilityMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 7, username: 'operator', role: 'OPERATOR' });
    listUnavailabilityMock.mockResolvedValue([SAMPLE_ENTRY]);
  });

  it('creates an unavailability with date-only inputs as a full-day window', async () => {
    seedAuth('OPERATOR');
    createUnavailabilityMock.mockResolvedValue(SAMPLE_ENTRY);
    render(
      <AuthProvider>
        <UnavailabilityPage />
      </AuthProvider>,
    );
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(
      screen.getByRole('button', { name: /new unavailability/i }),
    );
    await userEvent.type(screen.getByLabelText(/^from \*/i), '2026-09-28');
    await userEvent.type(screen.getByLabelText(/^to \*/i), '2026-09-28');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));

    await waitFor(() => {
      expect(createUnavailabilityMock).toHaveBeenCalledTimes(1);
    });
    expect(createUnavailabilityMock.mock.calls[0][0]).toMatchObject({
      userId: 7,
      startDateTime: '2026-09-28T00:00',
      endDateTime: '2026-09-28T23:59',
      reason: null,
    });
  });

  it('does not submit when required dates are missing and shows the error', async () => {
    seedAuth('OPERATOR');
    render(
      <AuthProvider>
        <UnavailabilityPage />
      </AuthProvider>,
    );
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(
      screen.getByRole('button', { name: /new unavailability/i }),
    );
    await userEvent.click(screen.getByRole('button', { name: /create/i }));

    expect(createUnavailabilityMock).not.toHaveBeenCalled();
    expect(await screen.findAllByText('This field is required.')).toHaveLength(
      2,
    );
  });
});
