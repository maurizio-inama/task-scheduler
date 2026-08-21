import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { TasksPage } from './TasksPage';

const listTasksMock = vi.fn();
const createTaskMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/tasksApi', () => ({
  tasksApi: {
    list: () => listTasksMock(),
    getById: vi.fn(),
    create: (...args: unknown[]) => createTaskMock(...args),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('../api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    me: () => meMock(),
  },
}));

const SAMPLE_TASK = {
  id: 25,
  title: 'Write documentation',
  description: null,
  status: 'PENDING',
  priority: 'HIGH',
  estimatedDurationMinutes: 90,
  deadline: '2026-09-01T17:00:00',
  createdAt: '2026-08-20T10:00:00',
};

function seedAuth(role: string): void {
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'user', role }),
  );
}

async function renderPage() {
  const view = render(
    <AuthProvider>
      <TasksPage />
    </AuthProvider>,
  );
  await waitFor(() => {
    expect(screen.getByRole('table')).toBeInTheDocument();
  });
  return view;
}

describe('TasksPage', () => {
  beforeEach(() => {
    localStorage.clear();
    listTasksMock.mockReset();
    createTaskMock.mockReset();
    meMock.mockReset();
    meMock.mockResolvedValue({ id: 1, username: 'user', role: 'ADMIN' });
    listTasksMock.mockResolvedValue([SAMPLE_TASK]);
  });

  it('renders the task table with formatted data', async () => {
    await renderPage();

    expect(screen.getByText('Write documentation')).toBeInTheDocument();
    expect(screen.getByText('PENDING')).toBeInTheDocument();
    expect(screen.getByText('1h 30m')).toBeInTheDocument();
    expect(screen.getByText('2026-09-01 17:00')).toBeInTheDocument();
  });

  it('shows an empty state when there are no tasks', async () => {
    listTasksMock.mockResolvedValue([]);
    render(
      <AuthProvider>
        <TasksPage />
      </AuthProvider>,
    );

    expect(
      await waitFor(() => screen.getByText(/no tasks yet/i)),
    ).toBeInTheDocument();
  });

  it('hides write actions from operators', async () => {
    seedAuth('OPERATOR');
    meMock.mockResolvedValue({ id: 3, username: 'op1', role: 'OPERATOR' });
    await renderPage();

    expect(screen.queryByRole('button', { name: /new task/i })).not.toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
  });

  it('shows write actions for reviewers', async () => {
    seedAuth('REVIEWER');
    await renderPage();

    expect(screen.getByRole('button', { name: /new task/i })).toBeInTheDocument();
  });

  it('validates required fields before submitting a new task', async () => {
    seedAuth('ADMIN');
    await renderPage();

    await userEvent.click(screen.getByRole('button', { name: /new task/i }));
    await userEvent.click(screen.getByRole('button', { name: /create task/i }));

    expect(await screen.findAllByText('This field is required.')).toHaveLength(2);
    expect(createTaskMock).not.toHaveBeenCalled();
  });

  it('creates a task with values from the form', async () => {
    seedAuth('ADMIN');
    createTaskMock.mockResolvedValue({ ...SAMPLE_TASK, id: 26 });
    await renderPage();

    await userEvent.click(screen.getByRole('button', { name: /new task/i }));
    await userEvent.type(screen.getByLabelText(/^title/i), 'New feature');
    await userEvent.type(
      screen.getByLabelText(/estimated duration/i),
      '60',
    );
    await userEvent.click(screen.getByRole('button', { name: /create task/i }));

    await waitFor(() => {
      expect(createTaskMock).toHaveBeenCalledTimes(1);
    });
    expect(createTaskMock.mock.calls[0][0]).toMatchObject({
      title: 'New feature',
      priority: 'MEDIUM',
      estimatedDurationMinutes: 60,
      deadline: null,
    });
  });

  it('surfaces server errors on failed creation', async () => {
    seedAuth('ADMIN');
    createTaskMock.mockRejectedValue(
      Object.assign(new Error('Title must be unique.'), { status: 409 }),
    );
    await renderPage();

    await userEvent.click(screen.getByRole('button', { name: /new task/i }));
    await userEvent.type(screen.getByLabelText(/^title/i), 'Duplicate');
    await userEvent.type(screen.getByLabelText(/estimated duration/i), '30');
    await userEvent.click(screen.getByRole('button', { name: /create task/i }));

    expect(await screen.findByText('Title must be unique.')).toBeInTheDocument();
  });
});
