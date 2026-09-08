import { useMemo, useState } from 'react';
import { useFetch } from '../hooks/useFetch';
import { useAuth } from '../context/AuthContext';
import { tasksApi } from '../api/tasksApi';
import { assignmentsApi } from '../api/assignmentsApi';
import { usersApi } from '../api/usersApi';
import TimelineHeader from './TimelineHeader';
import TimelineGrid from './TimelineGrid';
import TimelineLegend from './TimelineLegend';
import { Loading } from './Loading';
import {
  addDays,
  abbreviateTaskTitle,
  buildTimelineItems,
  buildWeek,
  formatWeekRange,
  mondayOfWeek,
  summarizeWeek,
} from '../utils/timeline';
import { getResourceColors, getTaskBorderColor } from '../utils/colors';
import type { Task, Assignment, User } from '../types/api';

export default function TaskTimeline() {
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState<Date>(() => mondayOfWeek(new Date()));

  const { data: tasks, loading: tasksLoading, error: tasksError, refetch: refetchTasks } =
    useFetch<Task[]>(() => tasksApi.list(), []);

  const { data: assignments, loading: assignmentsLoading, error: assignmentsError, refetch: refetchAssignments } =
    useFetch<Assignment[]>(() => assignmentsApi.list(), []);

  const { data: users } = useFetch<User[]>(
    () => user?.role === 'ADMIN' ? usersApi.list() : Promise.resolve([]),
    [user?.role],
  );

  const loading = tasksLoading || assignmentsLoading;
  const error = tasksError || assignmentsError;

  const week = useMemo(() => buildWeek(weekStart), [weekStart]);

  const items = useMemo(() => {
    if (!tasks || !assignments) return [];
    return buildTimelineItems(assignments, tasks, users ?? []);
  }, [assignments, tasks, users]);

  const legend = useMemo(() => {
    const summary = summarizeWeek(items, week);
    return {
      resources: summary.resources.map(r => ({
        id: r.userId,
        name: r.userName,
        color: getResourceColors(r.userId).bg,
      })),
      tasks: summary.tasks.map(t => ({
        id: t.taskId,
        name: abbreviateTaskTitle(t.taskTitle),
        color: getTaskBorderColor(t.taskId),
      })),
      hasItems: summary.resources.length > 0 || summary.tasks.length > 0,
    };
  }, [items, week]);

  const periodLabel = useMemo(() => formatWeekRange(week), [week]);

  const goPreviousWeek = () => setWeekStart(current => addDays(mondayOfWeek(current), -7));

  const goNextWeek = () => setWeekStart(current => addDays(mondayOfWeek(current), 7));

  const goToday = () => setWeekStart(mondayOfWeek(new Date()));

  const handleRetry = () => {
    refetchTasks();
    refetchAssignments();
  };

  return (
    <div className="task-timeline">
      <TimelineHeader
        periodLabel={periodLabel}
        onPrevious={goPreviousWeek}
        onNext={goNextWeek}
        onToday={goToday}
      />
      {loading && (
        <div className="timeline-status" role="status">
          <Loading label="Loading timeline data…" />
        </div>
      )}
      {!loading && error && (
        <div className="timeline-error" role="alert">
          <span>Unable to load timeline data.</span>
          <button type="button" className="timeline-retry" onClick={handleRetry}>
            Retry
          </button>
        </div>
      )}
      <div className="timeline-body">
        <div className="timeline-main">
          {!loading && !error && !legend.hasItems && (
            <div className="timeline-empty">
              No tasks scheduled for this week.
            </div>
          )}
          <TimelineGrid week={week} items={items} />
        </div>
        <TimelineLegend resources={legend.resources} tasks={legend.tasks} />
      </div>
    </div>
  );
}
