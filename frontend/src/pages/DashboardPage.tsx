import { assignmentsApi } from '../api/assignmentsApi';
import { schedulesApi } from '../api/schedulesApi';
import { tasksApi } from '../api/tasksApi';
import { EmptyState } from '../components/EmptyState';
import { Loading } from '../components/Loading';
import { useAuth } from '../context/AuthContext';
import { useFetch } from '../hooks/useFetch';
import { formatDateTime, formatDuration } from '../utils/format';
import type { TaskStatus } from '../types/api';

const STATUS_ORDER: TaskStatus[] = [
  'PENDING',
  'SCHEDULED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
];

export function DashboardPage() {
  const { user } = useAuth();

  const { data: tasks, loading: tasksLoading, error: tasksError } =
    useFetch(() => tasksApi.list(), []);
  const { data: schedules, loading: schedulesLoading, error: schedulesError } =
    useFetch(() => schedulesApi.list(), []);
  const {
    data: assignments,
    loading: assignmentsLoading,
    error: assignmentsError,
  } = useFetch(() => assignmentsApi.list(), []);

  if (tasksLoading || schedulesLoading || assignmentsLoading) {
    return (
      <div className="page">
        <h1>Dashboard</h1>
        <Loading />
      </div>
    );
  }

  const error = tasksError ?? schedulesError ?? assignmentsError;
  if (error) {
    return (
      <div className="page">
        <h1>Dashboard</h1>
        <div className="alert alert-error" role="alert">
          {error}
        </div>
      </div>
    );
  }

  const taskList = tasks ?? [];
  const scheduleList = (schedules ?? [])
    .slice()
    .sort((a, b) => a.startDateTime.localeCompare(b.startDateTime));
  const assignmentList = assignments ?? [];

  const pendingCount = taskList.filter((t) => t.status === 'PENDING').length;
  const totalMinutes = taskList
    .filter((t) => t.status === 'PENDING')
    .reduce((sum, t) => sum + t.estimatedDurationMinutes, 0);

  return (
    <div className="page">
      <div className="page-header">
        <h1>Dashboard</h1>
      </div>

      <p className="page-description">
        Welcome back, {user?.username}. Here is the current planning overview.
      </p>

      <div className="stat-grid">
        <div className="stat-card">
          <span className="stat-value">{taskList.length}</span>
          <span className="stat-label">Tasks</span>
        </div>
        <div className="stat-card">
          <span className="stat-value">{pendingCount}</span>
          <span className="stat-label">Pending tasks</span>
        </div>
        <div className="stat-card">
          <span className="stat-value">{formatDuration(totalMinutes)}</span>
          <span className="stat-label">Pending workload</span>
        </div>
        <div className="stat-card">
          <span className="stat-value">{scheduleList.length}</span>
          <span className="stat-label">Schedules</span>
        </div>
        <div className="stat-card">
          <span className="stat-value">{assignmentList.length}</span>
          <span className="stat-label">Assignments</span>
        </div>
      </div>

      <section className="dashboard-section">
        <h2>Tasks by status</h2>
        {taskList.length === 0 ? (
          <EmptyState message="No tasks yet." />
        ) : (
          <ul className="status-summary">
            {STATUS_ORDER.map((status) => {
              const count = taskList.filter((t) => t.status === status).length;
              return (
                <li key={status}>
                  <span className={`badge badge-${status.toLowerCase()}`}>
                    {status}
                  </span>
                  <strong>{count}</strong>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="dashboard-section">
        <h2>Upcoming schedule windows</h2>
        {scheduleList.length === 0 ? (
          <EmptyState message="No schedules yet." />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>From</th>
                <th>To</th>
              </tr>
            </thead>
            <tbody>
              {scheduleList.slice(0, 5).map((schedule) => (
                <tr key={schedule.id}>
                  <td>#{schedule.id}</td>
                  <td>{formatDateTime(schedule.startDateTime)}</td>
                  <td>{formatDateTime(schedule.endDateTime)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
