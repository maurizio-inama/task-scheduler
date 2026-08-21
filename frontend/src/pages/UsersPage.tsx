import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../api/client';
import { usersApi } from '../api/usersApi';
import { Badge } from '../components/Badge';
import { EmptyState } from '../components/EmptyState';
import { FormField } from '../components/FormField';
import { Loading } from '../components/Loading';
import { useFetch } from '../hooks/useFetch';
import { formatDate } from '../utils/format';
import {
  validateEmailField,
  validateRequiredFields,
  type Errors,
} from '../utils/validation';
import type { Role, User, UserInput } from '../types/api';

const ROLES: Role[] = ['ADMIN', 'REVIEWER', 'OPERATOR'];

interface FormValues {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  enabled: boolean;
}

const EMPTY_FORM: FormValues = {
  username: '',
  password: '',
  firstName: '',
  lastName: '',
  email: '',
  role: 'OPERATOR',
  enabled: true,
};

export function UsersPage() {
  const { data: users, loading, error, refetch } = useFetch(
    () => usersApi.list(),
    [],
  );

  const [editing, setEditing] = useState<User | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Errors<FormValues>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setValues(EMPTY_FORM);
    setFieldErrors({});
    setActionError(null);
    setFormOpen(true);
  };

  const openEdit = (user: User) => {
    setEditing(user);
    setValues({
      username: user.username,
      password: '',
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      role: user.role,
      enabled: user.enabled,
    });
    setFieldErrors({});
    setActionError(null);
    setFormOpen(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
  };

  const validate = (): boolean => {
    const nextErrors = validateRequiredFields(
      {
        username: values.username,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
      },
      ['username', 'password', 'firstName', 'lastName', 'email'],
    ) as Errors<FormValues>;

    const emailError = validateEmailField(values.email);
    if (emailError) {
      nextErrors.email = emailError;
    }

    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setActionError(null);
    if (!validate()) {
      return;
    }

    const input: UserInput = {
      username: values.username.trim(),
      password: values.password,
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      email: values.email.trim(),
      role: values.role,
      enabled: values.enabled,
    };

    setSaving(true);
    try {
      if (editing) {
        await usersApi.update(editing.id, input);
      } else {
        await usersApi.create(input);
      }
      closeForm();
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (user: User) => {
    setActionError(null);
    if (!window.confirm(`Delete user "${user.username}"? This cannot be undone.`)) {
      return;
    }
    try {
      await usersApi.remove(user.id);
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Users</h1>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          New user
        </button>
      </div>

      {error && (
        <div className="alert alert-error" role="alert">
          {error}
        </div>
      )}
      {actionError && (
        <div className="alert alert-error" role="alert">
          {actionError}
        </div>
      )}

      {loading ? (
        <Loading />
      ) : (
        <>
          {!users || users.length === 0 ? (
            <EmptyState message="No users found." />
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>{user.username}</td>
                    <td>
                      {user.firstName} {user.lastName}
                    </td>
                    <td>{user.email}</td>
                    <td>
                      <Badge value={user.role} />
                    </td>
                    <td>
                      <Badge value={user.enabled ? 'ENABLED' : 'DISABLED'}>
                        {user.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                    </td>
                    <td>{formatDate(user.createdAt)}</td>
                    <td className="table-actions">
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => openEdit(user)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => handleDelete(user)}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      {formOpen && (
        <div className="modal-backdrop">
          <form
            className="modal"
            onSubmit={handleSubmit}
            noValidate
            aria-label={editing ? 'Edit user' : 'New user'}
          >
            <h2>{editing ? `Edit user: ${editing.username}` : 'New user'}</h2>

            <FormField label="Username" htmlFor="user-username" required error={fieldErrors.username}>
              <input
                id="user-username"
                value={values.username}
                onChange={(e) => setValues({ ...values, username: e.target.value })}
                disabled={saving}
              />
            </FormField>

            <FormField
              label="Password"
              htmlFor="user-password"
              required
              error={fieldErrors.password}
              hint={
                editing
                  ? 'Re-enter the password to keep or change it (the API requires it on every update).'
                  : undefined
              }
            >
              <input
                id="user-password"
                type="password"
                autoComplete="new-password"
                value={values.password}
                onChange={(e) => setValues({ ...values, password: e.target.value })}
                disabled={saving}
              />
            </FormField>

            <div className="form-row">
              <FormField label="First name" htmlFor="user-first-name" required error={fieldErrors.firstName}>
                <input
                  id="user-first-name"
                  value={values.firstName}
                  onChange={(e) => setValues({ ...values, firstName: e.target.value })}
                  disabled={saving}
                />
              </FormField>

              <FormField label="Last name" htmlFor="user-last-name" required error={fieldErrors.lastName}>
                <input
                  id="user-last-name"
                  value={values.lastName}
                  onChange={(e) => setValues({ ...values, lastName: e.target.value })}
                  disabled={saving}
                />
              </FormField>
            </div>

            <FormField label="Email" htmlFor="user-email" required error={fieldErrors.email}>
              <input
                id="user-email"
                type="email"
                value={values.email}
                onChange={(e) => setValues({ ...values, email: e.target.value })}
                disabled={saving}
              />
            </FormField>

            <div className="form-row">
              <FormField label="Role" htmlFor="user-role" required>
                <select
                  id="user-role"
                  value={values.role}
                  onChange={(e) =>
                    setValues({ ...values, role: e.target.value as Role })
                  }
                  disabled={saving}
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </FormField>

              <FormField label="Status" htmlFor="user-enabled">
                <label className="checkbox-label" htmlFor="user-enabled">
                  <input
                    id="user-enabled"
                    type="checkbox"
                    checked={values.enabled}
                    onChange={(e) =>
                      setValues({ ...values, enabled: e.target.checked })
                    }
                    disabled={saving}
                  />
                  Enabled
                </label>
              </FormField>
            </div>

            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={closeForm}
                disabled={saving}
              >
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : editing ? 'Save changes' : 'Create user'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

function describe(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 409) {
      return cause.message;
    }
    return cause.message;
  }
  return cause instanceof Error ? cause.message : 'Unexpected error.';
}
