import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { AdminImportPage } from './AdminImportPage';

const validateFileMock = vi.fn();
const importFileMock = vi.fn();
const listScenariosMock = vi.fn();

vi.mock('../api/importApi', () => ({
  importApi: {
    validateFile: (...args: unknown[]) => validateFileMock(...args),
    importFile: (...args: unknown[]) => importFileMock(...args),
    listScenarios: () => listScenariosMock(),
    validateScenario: vi.fn(),
    importScenario: vi.fn(),
  },
}));

vi.mock('../api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    me: vi.fn().mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' }),
  },
}));

const VALID_REPORT = {
  valid: true,
  status: 'VALID',
  scenarioId: 'demo-basic',
  scenarioName: 'Basic Scheduling Demo',
  counts: { users: 5, tasks: 8, availabilities: 6, unavailabilities: 2, schedules: 1 },
  problems: [],
};

const CONFLICT_REPORT = {
  valid: false,
  status: 'CONFLICT',
  scenarioId: 'demo-basic',
  scenarioName: 'Basic Scheduling Demo',
  counts: { users: 5, tasks: 8, availabilities: 6, unavailabilities: 2, schedules: 1 },
  problems: ["users[0]: username 'dana_basic' already exists"],
};

const IMPORT_RESULT = {
  scenarioId: 'demo-basic',
  scenarioName: 'Basic Scheduling Demo',
  usersCreated: 5,
  tasksCreated: 8,
  availabilitiesCreated: 6,
  unavailabilitiesCreated: 2,
  schedulesCreated: 1,
  tasksScheduled: 8,
};

function seedAuth(role: string): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'user', role }),
  );
}

function selectFile(): Promise<void> {
  const jsonFile = new File(['{}'], 'demo.json', { type: 'application/json' });
  return userEvent.upload(screen.getByLabelText('Scenario JSON'), jsonFile);
}

describe('AdminImportPage', () => {
  beforeEach(() => {
    localStorage.clear();
    validateFileMock.mockReset();
    importFileMock.mockReset();
    listScenariosMock.mockReset();
    listScenariosMock.mockResolvedValue([]);
    seedAuth('ADMIN');
  });

  it('disables Validate and Import until a file is selected', async () => {
    render(
      <AuthProvider>
        <AdminImportPage />
      </AuthProvider>,
    );

    expect(screen.getByRole('button', { name: 'Validate' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled();

    await selectFile();

    expect(screen.getByTestId('selected-file')).toHaveTextContent('demo.json');
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Validate' })).toBeEnabled();
      expect(screen.getByRole('button', { name: 'Import' })).toBeEnabled();
    });
  });

  it('shows a VALID report after successful validation', async () => {
    validateFileMock.mockResolvedValue(VALID_REPORT);
    render(
      <AuthProvider>
        <AdminImportPage />
      </AuthProvider>,
    );
    await selectFile();

    await userEvent.click(screen.getByRole('button', { name: 'Validate' }));

    expect(await screen.findByText('Validation successful')).toBeInTheDocument();
    expect(screen.getByText(/Basic Scheduling Demo/)).toBeInTheDocument();
    expect(screen.getByText('VALID')).toBeInTheDocument();
    expect(validateFileMock).toHaveBeenCalledTimes(1);
  });

  it('lists conflict problems and keeps Import disabled after a failed validation', async () => {
    validateFileMock.mockResolvedValue(CONFLICT_REPORT);
    render(
      <AuthProvider>
        <AdminImportPage />
      </AuthProvider>,
    );
    await selectFile();

    await userEvent.click(screen.getByRole('button', { name: 'Validate' }));

    expect(await screen.findByText('Validation failed')).toBeInTheDocument();
    expect(screen.getByText("users[0]: username 'dana_basic' already exists")).toBeInTheDocument();
    expect(screen.getByText('CONFLICT')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled();
    expect(importFileMock).not.toHaveBeenCalled();
  });

  it('imports successfully and displays the result summary', async () => {
    importFileMock.mockResolvedValue(IMPORT_RESULT);
    render(
      <AuthProvider>
        <AdminImportPage />
      </AuthProvider>,
    );
    await selectFile();

    await userEvent.click(screen.getByRole('button', { name: 'Import' }));

    expect(await screen.findByText('Import completed')).toBeInTheDocument();
    expect(screen.getByText('IMPORTED')).toBeInTheDocument();
    expect(importFileMock).toHaveBeenCalledTimes(1);
  });

  it('shows the structured rejection when the import conflicts', async () => {
    importFileMock.mockRejectedValue(
      new (await import('../api/client')).ApiRequestError(
        409,
        'IMPORT_REJECTED',
        "users[0]: username 'dana_basic' already exists",
        ["users[0]: username 'dana_basic' already exists"],
      ),
    );
    render(
      <AuthProvider>
        <AdminImportPage />
      </AuthProvider>,
    );
    await selectFile();

    await userEvent.click(screen.getByRole('button', { name: 'Import' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "users[0]: username 'dana_basic' already exists",
    );
    expect(screen.getByText('CONFLICT')).toBeInTheDocument();
    expect(screen.queryByText('Import completed')).not.toBeInTheDocument();
  });
});
