import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../api/client';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from './LoginPage';

const loginMock = vi.fn();
const meMock = vi.fn();

vi.mock('../api/authApi', () => ({
  authApi: {
    login: (...args: unknown[]) => loginMock(...args),
    me: () => meMock(),
  },
}));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>home-page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
    loginMock.mockReset();
    meMock.mockReset();
  });

  it('renders the sign-in form', () => {
    renderLogin();

    expect(screen.getByRole('heading', { name: 'Task Scheduler' })).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /sign in/i }),
    ).toBeInTheDocument();
  });

  it('shows validation errors when submitting empty fields', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findAllByRole('alert')).toHaveLength(2);
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('shows an error message for invalid credentials', async () => {
    const user = userEvent.setup();
    loginMock.mockRejectedValue(
      new ApiRequestError(401, 'BAD_CREDENTIALS', 'Bad credentials'),
    );
    renderLogin();

    await user.type(screen.getByLabelText(/username/i), 'admin');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Invalid username or password.',
    );
    expect(loginMock).toHaveBeenCalledWith('admin', 'wrong');
  });

  it('logs in and navigates to the dashboard on success', async () => {
    const user = userEvent.setup();
    loginMock.mockResolvedValue({
      token: 'jwt',
      tokenType: 'Bearer',
      expiresIn: 3600,
      username: 'admin',
      role: 'ADMIN',
    });
    meMock.mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN' });
    renderLogin();

    await user.type(screen.getByLabelText(/username/i), 'admin');
    await user.type(screen.getByLabelText(/password/i), 'secret');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText('home-page')).toBeInTheDocument();
    });
    expect(JSON.parse(localStorage.getItem('task-scheduler-auth') ?? '{}')).toMatchObject({
      username: 'admin',
      role: 'ADMIN',
    });
  });
});
