import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../api/client';
import { assignmentsApi } from '../api/assignmentsApi';
import { schedulesApi } from '../api/schedulesApi';
import { tasksApi } from '../api/tasksApi';
import { usersApi } from '../api/usersApi';
import { Badge } from '../components/Badge';
import { DateOptionalTimeInput } from '../components/DateOptionalTimeInput';
import { EmptyState } from '../components/EmptyState';
import { FormField } from '../components/FormField';
import { Loading } from '../components/Loading';
import { useAuth } from '../context/AuthContext';
import { useFetch } from '../hooks/useFetch';
import { formatDateTime } from '../utils/format';
import {
  END_OF_DAY,
  START_OF_DAY,
  joinDateOptionalTime,
  splitDateTime,
} from '../utils/datetime';
import {
  validateDateRange,
  validateRequiredFields,
  type Errors,
} from '../utils/validation';
import type {
  Assignment,
  AssignmentInput,
  AssignmentStatus,
} from '../types/api';

const STATUSES: AssignmentStatus[] = [
  'ASSIGNED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
];

interface FormValues {
  userId: string;
  taskId: string;
  scheduleId: string;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  status: AssignmentStatus;
}

const EMPTY_FORM: FormValues = {
  userId: '',
  taskId: '',
  scheduleId: '',
  startDate: '',
  startTime: '',
  endDate: '',
  endTime: '',
  status: 'ASSIGNED',
};

export function AssignmentsPage() {
  const { user } = useAuth();
  const canWrite = user?.role === 'ADMIN' || user?.role === 'REVIEWER';

  const { data: assignments, loading, error, refetch } = useFetch(
    () => assignmentsApi.list(),
    [],
  );

  const { data: tasks } = useFetch(() => tasksApi.list(), []);
  const { data: schedules } = useFetch(() => schedulesApi.list(), []);

  const [editing, setEditing] = useState<Assignment | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Errors<FormValues>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setValues({
      ...EMPTY_FORM,
      taskId: String(tasks?.[0]?.id ?? ''),
      scheduleId: String(schedules?.[0]?.id ?? ''),
    });
    setFieldErrors({});
    setActionError(null);
    setFormOpen(true);
  };

  const openEdit = (assignment: Assignment) => {
    setEditing(assignment);
    setValues({
      userId: String(assignment.userId),
      taskId: String(assignment.taskId),
      scheduleId: String(assignment.scheduleId),
      startDate: splitDateTime(assignment.startDateTime).date,
      startTime: splitDateTime(assignment.startDateTime).time,
      endDate: splitDateTime(assignment.endDateTime).date,
      endTime: splitDateTime(assignment.endDateTime).time,
      status: assignment.status,
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
        userId: values.userId,
        taskId: values.taskId,
        scheduleId: values.scheduleId,
        startDate: values.startDate,
        endDate: values.endDate,
      },
      ['userId', 'taskId', 'scheduleId', 'startDate', 'endDate'],
    ) as Errors<FormValues>;

    const start = joinDateOptionalTime(
      values.startDate,
      values.startTime,
      START_OF_DAY,
    );
    const end = joinDateOptionalTime(
      values.endDate,
      values.endTime,
      END_OF_DAY,
    );
    if (start && end) {
      const rangeError = validateDateRange(start, end);
      if (rangeError) {
        nextErrors.endDate = rangeError;
      }
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

    const input: AssignmentInput = {
      userId: Number(values.userId),
      taskId: Number(values.taskId),
      scheduleId: Number(values.scheduleId),
      startDateTime: joinDateOptionalTime(
        values.startDate,
        values.startTime,
        START_OF_DAY,
      )!,
      endDateTime: joinDateOptionalTime(
        values.endDate,
        values.endTime,
        END_OF_DAY,
      )!,
      status: values.status,
    };

    setSaving(true);
    try {
      if (editing) {
        await assignmentsApi.update(editing.id, input);
      } else {
        await assignmentsApi.create(input);
      }
      closeForm();
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (assignment: Assignment) => {
    setActionError(null);
    if (!window.confirm(`Delete assignment #${assignment.id}?`)) {
      return;
    }
    try {
      await assignmentsApi.remove(assignment.id);
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    }
  };

  const taskTitle = (taskId: number): string => {
    const found = tasks?.find((task) => task.id === taskId);
    return found ? found.title : `Task #${taskId}`;
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Assignments</h1>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            New assignment
          </button>
        )}
      </div>

      <p className="page-description">
        Individual task allocations to users inside a schedule window.
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
      ) : !assignments || assignments.length === 0 ? (
        <EmptyState message="No assignments yet." />
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Task</th>
              <th>User</th>
              <th>Schedule</th>
              <th>Window</th>
              <th>Status</th>
              {canWrite && <th aria-label="Actions" />}
            </tr>
          </thead>
          <tbody>
            {assignments.map((assignment) => (
              <tr key={assignment.id}>
                <td>{taskTitle(assignment.taskId)}</td>
                <td>User #{assignment.userId}</td>
                <td>#{assignment.scheduleId}</td>
                <td>
                  {formatDateTime(assignment.startDateTime)} →{' '}
                  {formatDateTime(assignment.endDateTime)}
                </td>
                <td>
                  <Badge value={assignment.status} />
                </td>
                {canWrite && (
                  <td className="table-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={() => openEdit(assignment)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => handleDelete(assignment)}
                    >
                      Delete
                    </button>
                  </td>
                )}
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
            aria-label={editing ? 'Edit assignment' : 'New assignment'}
          >
            <h2>{editing ? `Edit assignment #${editing.id}` : 'New assignment'}</h2>

            <FormField
              label="User ID"
              htmlFor="assign-user"
              required
              error={fieldErrors.userId}
              hint={
                user?.role === 'ADMIN'
                  ? undefined
                  : 'Only administrators can list users — enter the numeric user ID.'
              }
            >
              {user?.role === 'ADMIN' ? (
                <AdminUserSelect
                  value={values.userId}
                  onChange={(value) => setValues({ ...values, userId: value })}
                  disabled={saving}
                />
              ) : (
                <input
                  id="assign-user"
                  type="number"
                  min={1}
                  step={1}
                  value={values.userId}
                  onChange={(e) =>
                    setValues({ ...values, userId: e.target.value })
                  }
                  disabled={saving}
                />
              )}
            </FormField>

            <div className="form-row">
              <FormField label="Task" htmlFor="assign-task" required error={fieldErrors.taskId}>
                <select
                  id="assign-task"
                  value={values.taskId}
                  onChange={(e) =>
                    setValues({ ...values, taskId: e.target.value })
                  }
                  disabled={saving}
                >
                  <option value="">Select a task…</option>
                  {(tasks ?? []).map((task) => (
                    <option key={task.id} value={String(task.id)}>
                      #{task.id} — {task.title}
                    </option>
                  ))}
                </select>
              </FormField>

              <FormField label="Schedule" htmlFor="assign-schedule" required error={fieldErrors.scheduleId}>
                <select
                  id="assign-schedule"
                  value={values.scheduleId}
                  onChange={(e) =>
                    setValues({ ...values, scheduleId: e.target.value })
                  }
                  disabled={saving}
                >
                  <option value="">Select a schedule…</option>
                  {(schedules ?? []).map((schedule) => (
                    <option key={schedule.id} value={String(schedule.id)}>
                      #{schedule.id} ({formatDateTime(schedule.startDateTime)})
                    </option>
                  ))}
                </select>
              </FormField>
            </div>

            <div className="form-row">
              <FormField
                label="From"
                htmlFor="assign-start"
                required
                hint={
                  values.startDate
                    ? `Will start at ${values.startTime || START_OF_DAY}`
                    : 'Optional — leave the time empty for start of day (00:00)'
                }
                error={fieldErrors.startDate}
              >
                <DateOptionalTimeInput
                  id="assign-start"
                  timeLabel="From time"
                  dateValue={values.startDate}
                  timeValue={values.startTime}
                  onDateChange={(date) =>
                    setValues({ ...values, startDate: date })
                  }
                  onTimeChange={(time) =>
                    setValues({ ...values, startTime: time })
                  }
                  disabled={saving}
                />
              </FormField>

              <FormField
                label="To"
                htmlFor="assign-end"
                required
                hint={
                  values.endDate
                    ? `Will end at ${values.endTime || END_OF_DAY}`
                    : 'Optional — leave the time empty for end of day (23:59)'
                }
                error={fieldErrors.endDate}
              >
                <DateOptionalTimeInput
                  id="assign-end"
                  timeLabel="To time"
                  dateValue={values.endDate}
                  timeValue={values.endTime}
                  onDateChange={(date) => setValues({ ...values, endDate: date })}
                  onTimeChange={(time) => setValues({ ...values, endTime: time })}
                  disabled={saving}
                />
              </FormField>
            </div>

            <FormField label="Status" htmlFor="assign-status" required>
              <select
                id="assign-status"
                value={values.status}
                onChange={(e) =>
                  setValues({
                    ...values,
                    status: e.target.value as AssignmentStatus,
                  })
                }
                disabled={saving}
              >
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
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

function AdminUserSelect({
  value,
  onChange,
  disabled,
}: {
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}) {
  const { data: users, loading, error } = useFetch(() => usersApi.list(), []);

  if (loading) {
    return <p className="form-hint">Loading users…</p>;
  }
  if (error) {
    return (
      <input
        type="number"
        min={1}
        step={1}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        aria-label="User ID"
      />
    );
  }

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      aria-label="User ID"
    >
      <option value="">Select a user…</option>
      {(users ?? []).map((candidate) => (
        <option key={candidate.id} value={String(candidate.id)}>
          {candidate.username} ({candidate.role})
        </option>
      ))}
    </select>
  );
}

function describe(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    return cause.message;
  }
  return cause instanceof Error ? cause.message : 'Unexpected error.';
}
