import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../api/client';
import { unavailabilityApi } from '../api/unavailabilityApi';
import { usersApi } from '../api/usersApi';
import { EmptyState } from '../components/EmptyState';
import { FormField } from '../components/FormField';
import { Loading } from '../components/Loading';
import { useAuth } from '../context/AuthContext';
import { useFetch } from '../hooks/useFetch';
import { formatDateTime, toDateTimeInputValue } from '../utils/format';
import {
  validateDateRange,
  validateRequiredFields,
  type Errors,
} from '../utils/validation';
import type {
  Unavailability,
  UnavailabilityInput,
  User,
} from '../types/api';

interface FormValues {
  userId: string;
  startDateTime: string;
  endDateTime: string;
  reason: string;
}

const EMPTY_FORM: FormValues = {
  userId: '',
  startDateTime: '',
  endDateTime: '',
  reason: '',
};

export function UnavailabilityPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const { data: entries, loading, error, refetch } = useFetch(
    () => unavailabilityApi.list(),
    [],
  );

  const { data: users } = useFetch(
    () => (isAdmin ? usersApi.list() : Promise.resolve<User[]>([])),
    [isAdmin],
  );

  const [editing, setEditing] = useState<Unavailability | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Errors<FormValues>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const targetUserId = (): number => {
    if (isAdmin) {
      return Number(values.userId);
    }
    return user?.id ?? 0;
  };

  const openCreate = () => {
    setEditing(null);
    setValues({
      ...EMPTY_FORM,
      userId: isAdmin ? String(users?.[0]?.id ?? '') : String(user?.id ?? ''),
    });
    setFieldErrors({});
    setActionError(null);
    setFormOpen(true);
  };

  const openEdit = (entry: Unavailability) => {
    if (!isAdmin && entry.userId !== user?.id) {
      return;
    }
    setEditing(entry);
    setValues({
      userId: String(entry.userId),
      startDateTime: toDateTimeInputValue(entry.startDateTime),
      endDateTime: toDateTimeInputValue(entry.endDateTime),
      reason: entry.reason ?? '',
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
        startDateTime: values.startDateTime,
        endDateTime: values.endDateTime,
      },
      ['startDateTime', 'endDateTime'],
    ) as Errors<FormValues>;

    if (isAdmin && !values.userId) {
      nextErrors.userId = 'Select a user.';
    }

    const rangeError = validateDateRange(
      values.startDateTime,
      values.endDateTime,
    );
    if (rangeError) {
      nextErrors.endDateTime = rangeError;
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

    const input: UnavailabilityInput = {
      userId: targetUserId(),
      startDateTime: values.startDateTime,
      endDateTime: values.endDateTime,
      reason:
        values.reason.trim().length > 0 ? values.reason.trim() : null,
    };

    setSaving(true);
    try {
      if (editing) {
        await unavailabilityApi.update(editing.id, input);
      } else {
        await unavailabilityApi.create(input);
      }
      closeForm();
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (entry: Unavailability) => {
    setActionError(null);
    if (!window.confirm('Delete this unavailability window?')) {
      return;
    }
    try {
      await unavailabilityApi.remove(entry.id);
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    }
  };

  const userName = (userId: number): string => {
    if (userId === user?.id) {
      return `${user.username} (you)`;
    }
    const found = users?.find((candidate) => candidate.id === userId);
    return found ? found.username : `User #${userId}`;
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Unavailability</h1>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          New unavailability
        </button>
      </div>

      <p className="page-description">
        Windows during which users cannot be scheduled (time off, meetings…).
        {!isAdmin && ' You are managing your own unavailability.'}
      </p>

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
      ) : !entries || entries.length === 0 ? (
        <EmptyState message="No unavailability windows recorded yet." />
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>User</th>
              <th>From</th>
              <th>To</th>
              <th>Reason</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id}>
                <td>{userName(entry.userId)}</td>
                <td>{formatDateTime(entry.startDateTime)}</td>
                <td>{formatDateTime(entry.endDateTime)}</td>
                <td>{entry.reason ?? '—'}</td>
                <td className="table-actions">
                  {isAdmin || entry.userId === user?.id ? (
                    <>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => openEdit(entry)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => handleDelete(entry)}
                      >
                        Delete
                      </button>
                    </>
                  ) : (
                    <span className="cell-muted">Read-only</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {formOpen && (
        <div className="modal-backdrop">
          <form
            className="modal"
            onSubmit={handleSubmit}
            noValidate
            aria-label={editing ? 'Edit unavailability' : 'New unavailability'}
          >
            <h2>{editing ? 'Edit unavailability' : 'New unavailability'}</h2>

            {isAdmin ? (
              <FormField label="User" htmlFor="unavail-user" required error={fieldErrors.userId}>
                <select
                  id="unavail-user"
                  value={values.userId}
                  onChange={(e) =>
                    setValues({ ...values, userId: e.target.value })
                  }
                  disabled={saving}
                >
                  <option value="">Select a user…</option>
                  {(users ?? []).map((candidate) => (
                    <option key={candidate.id} value={String(candidate.id)}>
                      {candidate.username} ({candidate.role})
                    </option>
                  ))}
                </select>
              </FormField>
            ) : (
              <p className="form-hint">
                Recording unavailability for <strong>{user?.username}</strong>.
              </p>
            )}

            <div className="form-row">
              <FormField label="From" htmlFor="unavail-start" required error={fieldErrors.startDateTime}>
                <input
                  id="unavail-start"
                  type="datetime-local"
                  value={values.startDateTime}
                  onChange={(e) =>
                    setValues({ ...values, startDateTime: e.target.value })
                  }
                  disabled={saving}
                />
              </FormField>

              <FormField label="To" htmlFor="unavail-end" required error={fieldErrors.endDateTime}>
                <input
                  id="unavail-end"
                  type="datetime-local"
                  value={values.endDateTime}
                  onChange={(e) =>
                    setValues({ ...values, endDateTime: e.target.value })
                  }
                  disabled={saving}
                />
              </FormField>
            </div>

            <FormField label="Reason" htmlFor="unavail-reason" hint="Optional">
              <input
                id="unavail-reason"
                type="text"
                value={values.reason}
                onChange={(e) =>
                  setValues({ ...values, reason: e.target.value })
                }
                disabled={saving}
              />
            </FormField>

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
                {saving ? 'Saving…' : editing ? 'Save changes' : 'Create'}
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
    return cause.message;
  }
  return cause instanceof Error ? cause.message : 'Unexpected error.';
}
