import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { AuthProvider } from './context/AuthContext';

const meMock = vi.fn();
const emptyList = vi.fn().mockResolvedValue([]);

vi.mock('./api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    me: () => meMock(),
  },
}));

vi.mock('./api/tasksApi', () => ({ tasksApi: { list: () => emptyList() } }));
vi.mock('./api/schedulesApi', () => ({
  schedulesApi: { list: () => emptyList() },
}));
vi.mock('./api/assignmentsApi', () => ({
  assignmentsApi: { list: () => emptyList() },
}));

function seedAuth(role: string | null): void {
  if (role === null) {
    localStorage.removeItem('task-scheduler-auth');
    return;
  }
  localStorage.setItem(
    'task-scheduler-auth',
    JSON.stringify({ token: 'jwt', username: 'user', role }),
  );
}

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('App routing and authorization', () => {
  beforeEach(() => {
    localStorage.clear();
    emptyList.mockResolvedValue([]);
    meMock.mockReset();
  });

  it('redirects unauthenticated visitors to the login page', async () => {
    seedAuth(null);

    renderApp();

    expect(
      await screen.findByRole('heading', { name: 'Task Scheduler' }),
    ).toBeInTheDocument();
    expect(meMock).not.toHaveBeenCalled();
  });

  it('shows the dashboard and standard navigation for operators', async () => {
    seedAuth('OPERATOR');
    meMock.mockResolvedValue({ id: 3, username: 'op1', role: 'OPERATOR' });
    renderApp();

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'Dashboard' }),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: 'Tasks' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Tasks' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Availability' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
  });

  it('exposes the Users navigation entry to admins only', async () => {
    seedAuth('ADMIN');
    meMock.mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' });

    renderApp();

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Users' })).toBeInTheDocument();
    });
  });
});
