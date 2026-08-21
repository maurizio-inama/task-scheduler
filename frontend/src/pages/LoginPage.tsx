import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { FormField } from '../components/FormField';
import { Loading } from '../components/Loading';
import { validateRequiredFields } from '../utils/validation';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const { user, initializing, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as LocationState | null;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (initializing) {
    return <Loading label="Restoring your session…" />;
  }

  if (user) {
    return <Navigate to={state?.from ?? '/'} replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError(null);

    const errors = validateRequiredFields({ username, password }, [
      'username',
      'password',
    ]);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await login(username.trim(), password);
      navigate(state?.from ?? '/', { replace: true });
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        setFormError('Invalid username or password.');
      } else if (cause instanceof Error) {
        setFormError(cause.message);
      } else {
        setFormError('Sign in failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit} noValidate>
        <h1>Task Scheduler</h1>
        <p className="login-subtitle">Sign in to your account</p>

        {formError && (
          <div className="alert alert-error" role="alert">
            {formError}
          </div>
        )}

        <FormField label="Username" htmlFor="username" required error={fieldErrors.username}>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={submitting}
          />
        </FormField>

        <FormField label="Password" htmlFor="password" required error={fieldErrors.password}>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={submitting}
          />
        </FormField>

        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={submitting}
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
