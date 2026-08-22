import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../api/client';
import { schedulesApi } from '../api/schedulesApi';
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
} from '../utils/datetime';
import {
  validateDateRange,
  validateRequiredFields,
  type Errors,
} from '../utils/validation';
import type {
  GenerateResponse,
  Schedule,
  ScheduleInput,
  ScheduleStatus,
} from '../types/api';

const STATUSES: ScheduleStatus[] = ['DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELLED'];

interface FormValues {
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  status: ScheduleStatus;
}

const EMPTY_FORM: FormValues = {
  startDate: '',
  startTime: '',
  endDate: '',
  endTime: '',
  status: 'DRAFT',
};

export function SchedulesPage() {
  const { user } = useAuth();
  const canWrite = user?.role === 'ADMIN' || user?.role === 'REVIEWER';

  const { data: schedules, loading, error, refetch } = useFetch(
    () => schedulesApi.list(),
    [],
  );

  const [formOpen, setFormOpen] = useState(false);
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Errors<FormValues>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  const [generateResult, setGenerateResult] =
    useState<GenerateResponse | null>(null);

  const openCreate = () => {
    setValues(EMPTY_FORM);
    setFieldErrors({});
    setActionError(null);
    setFormOpen(true);
  };

  const closeForm = () => {
    setFormOpen(false);
  };

  const validate = (): boolean => {
    const nextErrors = validateRequiredFields(
      {
        startDate: values.startDate,
        endDate: values.endDate,
      },
      ['startDate', 'endDate'],
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

    const input: ScheduleInput = {
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
      await schedulesApi.create(input);
      closeForm();
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (schedule: Schedule) => {
    setActionError(null);
    if (
      !window.confirm(
        `Delete schedule #${schedule.id}? Its assignments will be removed as well.`,
      )
    ) {
      return;
    }
    try {
      await schedulesApi.remove(schedule.id);
      setGenerateResult(null);
      refetch();
    } catch (cause) {
      setActionError(describe(cause));
    }
  };

  const handleGenerate = async (schedule: Schedule) => {
    setActionError(null);
    setGeneratingId(schedule.id);
    try {
      const result = await schedulesApi.generate(schedule.id);
      setGenerateResult(result);
      refetch();
    } catch (cause) {
      setGenerateResult(null);
      setActionError(describe(cause));
    } finally {
      setGeneratingId(null);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Schedules</h1>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            New schedule
          </button>
        )}
      </div>

      <p className="page-description">
        A schedule groups task assignments within a planning window. Use
        Generate to run the scheduling engine: pending tasks are allocated to
        available users inside the window.
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
      {generateResult && (
        <div
          className={
            generateResult.unscheduledTasks.length === 0
              ? 'alert alert-success'
              : 'alert alert-warning'
          }
          role="status"
        >
          <strong>Schedule #{generateResult.scheduleId} generated.</strong>{' '}
          {generateResult.scheduledTaskCount} task(s) scheduled with{' '}
          {generateResult.createdAssignmentCount} assignment(s).
          {generateResult.unscheduledTasks.length > 0 && (
            <ul className="alert-list">
              {generateResult.unscheduledTasks.map((entry) => (
                <li key={entry.taskId}>
                  Task #{entry.taskId}: {entry.reason}
                  {entry.detail ? ` — ${entry.detail}` : ''}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {loading ? (
        <Loading />
      ) : !schedules || schedules.length === 0 ? (
        <EmptyState message="No schedules yet. Create one to start planning." />
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Window</th>
              <th>Created</th>
              {canWrite && <th aria-label="Actions" />}
            </tr>
          </thead>
          <tbody>
            {schedules.map((schedule) => (
              <tr key={schedule.id}>
                <td>#{schedule.id}</td>
                <td>
                  {formatDateTime(schedule.startDateTime)} →{' '}
                  {formatDateTime(schedule.endDateTime)}
                </td>
                <td>{formatDateTime(schedule.createdAt)}</td>
                {canWrite && (
                  <td className="table-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={() => handleGenerate(schedule)}
                      disabled={generatingId !== null}
                    >
                      {generatingId === schedule.id
                        ? 'Generating…'
                        : 'Generate'}
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => handleDelete(schedule)}
                      disabled={generatingId !== null}
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
            aria-label="New schedule"
          >
            <h2>New schedule</h2>

            <div className="form-row">
              <FormField
                label="From"
                htmlFor="sched-start"
                required
                hint={
                  values.startDate
                    ? `Will start at ${values.startTime || START_OF_DAY}`
                    : 'Optional — leave the time empty for start of day (00:00)'
                }
                error={fieldErrors.startDate}
              >
                <DateOptionalTimeInput
                  id="sched-start"
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
                htmlFor="sched-end"
                required
                hint={
                  values.endDate
                    ? `Will end at ${values.endTime || END_OF_DAY}`
                    : 'Optional — leave the time empty for end of day (23:59)'
                }
                error={fieldErrors.endDate}
              >
                <DateOptionalTimeInput
                  id="sched-end"
                  timeLabel="To time"
                  dateValue={values.endDate}
                  timeValue={values.endTime}
                  onDateChange={(date) => setValues({ ...values, endDate: date })}
                  onTimeChange={(time) => setValues({ ...values, endTime: time })}
                  disabled={saving}
                />
              </FormField>
            </div>

            <FormField
              label="Initial status"
              htmlFor="sched-status"
              hint="Only set at creation time; the API does not expose status on read."
            >
              <select
                id="sched-status"
                value={values.status}
                onChange={(e) =>
                  setValues({ ...values, status: e.target.value as ScheduleStatus })
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
                {saving ? 'Creating…' : 'Create schedule'}
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
