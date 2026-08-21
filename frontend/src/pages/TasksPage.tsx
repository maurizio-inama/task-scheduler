import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../api/client';
import { tasksApi } from '../api/tasksApi';
import { Badge } from '../components/Badge';
import { EmptyState } from '../components/EmptyState';
import { FormField } from '../components/FormField';
import { Loading } from '../components/Loading';
import { useAuth } from '../context/AuthContext';
import { useFetch } from '../hooks/useFetch';
import {
  formatDateTime,
  formatDuration,
  toDateTimeInputValue,
} from '../utils/format';
import {
  validatePositiveNumber,
  validateRequiredFields,
  type Errors,
} from '../utils/validation';
import type {
  Task,
  TaskInput,
  TaskPriority,
  TaskStatus,
} from '../types/api';

const STATUSES: TaskStatus[] = [
  'PENDING',
  'SCHEDULED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
];

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

interface FormValues {
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  estimatedDurationMinutes: string;
  deadline: string;
}

const EMPTY_FORM: FormValues = {
  title: '',
  description: '',
  status: 'PENDING',
  priority: 'MEDIUM',
  estimatedDurationMinutes: '',
  deadline: '',
};

export function TasksPage() {
  const { user } = useAuth();
  const canWrite = user?.role === 'ADMIN' || user?.role === 'REVIEWER';

  const { data: tasks, loading, error, refetch } = useFetch(
    () => tasksApi.list(),
    [],
  );

  const [editing, setEditing] = useState<Task | null>(null);
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

  const openEdit = (task: Task) => {
    setEditing(task);
    setValues({
      title: task.title,
      description: task.description ?? '',
      status: task.status,
      priority: task.priority,
      estimatedDurationMinutes: String(task.estimatedDurationMinutes),
      deadline: toDateTimeInputValue(task.deadline),
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
      { title: values.title, estimatedDurationMinutes: values.estimatedDurationMinutes },
      ['title', 'estimatedDurationMinutes'],
    ) as Errors<FormValues>;

    if (!nextErrors.estimatedDurationMinutes) {
      const durationError = validatePositiveNumber(
        values.estimatedDurationMinutes === ''
          ? ''
          : Number(values.estimatedDurationMinutes),
        'Estimated duration',
      );
      if (durationError) {
        nextErrors.estimatedDurationMinutes = durationError;
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

    const input: TaskInput = {
      title: values.title.trim(),
      description:
        values.description.trim().length > 0 ? values.description.trim() : null,
      status: values.status,
      priority: values.priority,
      estimatedDurationMinutes: Number(values.estimatedDurationMinutes),
      deadline: values.deadline.length > 0 ? values.deadline : null,
    };

    setSaving(true);
    try {
      if (editing) {
        await tasksApi.update(editing.id, input);
      } else {
        await tasksApi.create(input);
      }
      closeForm();
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (task: Task) => {
    setActionError(null);
    if (!window.confirm(`Delete task "${task.title}"? This cannot be undone.`)) {
      return;
    }
    try {
      await tasksApi.remove(task.id);
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Tasks</h1>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            New task
          </button>
        )}
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
      ) : !tasks || tasks.length === 0 ? (
        <EmptyState message="No tasks yet. Create the first one to get started." />
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Duration</th>
              <th>Deadline</th>
              <th>Created</th>
              {canWrite && <th aria-label="Actions" />}
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.id}>
                <td>
                  <span className="cell-title">{task.title}</span>
                  {task.description && (
                    <span className="cell-subtitle">{task.description}</span>
                  )}
                </td>
                <td>
                  <Badge value={task.status} />
                </td>
                <td>
                  <Badge value={`priority-${task.priority.toLowerCase()}`}>
                    {task.priority}
                  </Badge>
                </td>
                <td>{formatDuration(task.estimatedDurationMinutes)}</td>
                <td>{formatDateTime(task.deadline)}</td>
                <td>{formatDateTime(task.createdAt)}</td>
                {canWrite && (
                  <td className="table-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={() => openEdit(task)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => handleDelete(task)}
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
            aria-label={editing ? 'Edit task' : 'New task'}
          >
            <h2>{editing ? `Edit task: ${editing.title}` : 'New task'}</h2>

            <FormField label="Title" htmlFor="task-title" required error={fieldErrors.title}>
              <input
                id="task-title"
                value={values.title}
                onChange={(e) => setValues({ ...values, title: e.target.value })}
                disabled={saving}
              />
            </FormField>

            <FormField label="Description" htmlFor="task-description" error={fieldErrors.description}>
              <textarea
                id="task-description"
                rows={3}
                value={values.description}
                onChange={(e) =>
                  setValues({ ...values, description: e.target.value })
                }
                disabled={saving}
              />
            </FormField>

            <div className="form-row">
              <FormField label="Status" htmlFor="task-status" required>
                <select
                  id="task-status"
                  value={values.status}
                  onChange={(e) =>
                    setValues({ ...values, status: e.target.value as TaskStatus })
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

              <FormField label="Priority" htmlFor="task-priority" required>
                <select
                  id="task-priority"
                  value={values.priority}
                  onChange={(e) =>
                    setValues({
                      ...values,
                      priority: e.target.value as TaskPriority,
                    })
                  }
                  disabled={saving}
                >
                  {PRIORITIES.map((priority) => (
                    <option key={priority} value={priority}>
                      {priority}
                    </option>
                  ))}
                </select>
              </FormField>
            </div>

            <div className="form-row">
              <FormField
                label="Estimated duration (minutes)"
                htmlFor="task-duration"
                required
                error={fieldErrors.estimatedDurationMinutes}
              >
                <input
                  id="task-duration"
                  type="number"
                  min={1}
                  step={1}
                  value={values.estimatedDurationMinutes}
                  onChange={(e) =>
                    setValues({
                      ...values,
                      estimatedDurationMinutes: e.target.value,
                    })
                  }
                  disabled={saving}
                />
              </FormField>

              <FormField label="Deadline" htmlFor="task-deadline" hint="Optional">
                <input
                  id="task-deadline"
                  type="datetime-local"
                  value={values.deadline}
                  onChange={(e) =>
                    setValues({ ...values, deadline: e.target.value })
                  }
                  disabled={saving}
                />
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
                {saving ? 'Saving…' : editing ? 'Save changes' : 'Create task'}
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
