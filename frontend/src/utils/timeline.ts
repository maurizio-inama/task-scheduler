import type { Assignment, Task, User } from '../types/api';

export interface TimelineDay {
  date: string;
  dayOfWeek: number;
  isToday: boolean;
  dayLabel: string;
  monthIndex: number;
  dayOfMonth: number;
  year: number;
  showMonthYear: boolean;
  monthContextLabel: string;
}

export interface TimelineWeek {
  weekStart: string;
  days: TimelineDay[];
}

export interface TimelineItem {
  userId: number;
  userName: string;
  taskId: number;
  taskTitle: string;
  taskPriority: string;
  startDateTime: string;
  endDateTime: string;
}

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];
const MONTH_NAMES_FULL = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export function mondayOfWeek(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = (day + 6) % 7;
  d.setDate(d.getDate() - diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function formatDateStr(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function buildWeek(weekStartDate: Date): TimelineWeek {
  const weekStart = mondayOfWeek(weekStartDate);
  const todayStr = formatDateStr(new Date());

  const days: TimelineDay[] = [];
  for (let d = 0; d < 7; d++) {
    const dayDate = addDays(weekStart, d);
    const dateStr = formatDateStr(dayDate);
    const isToday = dateStr === todayStr;
    const dayOfMonth = dayDate.getDate();
    const year = dayDate.getFullYear();
    const monthIndex = dayDate.getMonth();
    const next = addDays(dayDate, 1);
    days.push({
      date: dateStr,
      dayOfWeek: d,
      isToday,
      dayLabel: DAY_NAMES[d],
      monthIndex,
      dayOfMonth,
      year,
      showMonthYear:
        dayOfMonth === 1 ||
        next.getMonth() !== monthIndex ||
        isToday,
      monthContextLabel: `${MONTH_NAMES[monthIndex]}${
        dayOfMonth === 1 ? ` ${year}` : ''
      }`,
    });
  }

  return {
    weekStart: formatDateStr(weekStart),
    days,
  };
}

export function buildWeeks(weeksBefore: number, weeksAfter: number): TimelineWeek[] {
  const monday = mondayOfWeek(new Date());
  const weeks: TimelineWeek[] = [];

  for (let w = -weeksBefore; w <= weeksAfter; w++) {
    weeks.push(buildWeek(addDays(monday, w * 7)));
  }

  return weeks;
}

export function formatWeekRange(week: TimelineWeek): string {
  const first = week.days[0];
  const last = week.days[6];
  const firstLabel = `${MONTH_NAMES_FULL[first.monthIndex]} ${first.dayOfMonth}`;
  const sameMonth = first.monthIndex === last.monthIndex;
  const lastLabel = sameMonth
    ? String(last.dayOfMonth)
    : `${MONTH_NAMES_FULL[last.monthIndex]} ${last.dayOfMonth}`;

  if (first.year === last.year) {
    return `${firstLabel} – ${lastLabel}, ${last.year}`;
  }
  return `${firstLabel}, ${first.year} – ${lastLabel}, ${last.year}`;
}

export function buildTimelineItems(
  assignments: Assignment[],
  tasks: Task[],
  users: User[],
): TimelineItem[] {
  const taskMap = new Map<number, Task>();
  tasks.forEach(t => taskMap.set(t.id, t));

  const userMap = new Map<number, User>();
  users.forEach(u => userMap.set(u.id, u));

  const items = assignments
    .filter(a => a.status !== 'CANCELLED')
    .map(a => {
      const task = taskMap.get(a.taskId);
      const user = userMap.get(a.userId);
      return {
        userId: a.userId,
        userName: user?.username ?? `User #${a.userId}`,
        taskId: a.taskId,
        taskTitle: task?.title ?? `Task #${a.taskId}`,
        taskPriority: task?.priority ?? 'MEDIUM',
        startDateTime: a.startDateTime,
        endDateTime: a.endDateTime,
      };
    });

  items.sort(
    (a, b) =>
      a.startDateTime.localeCompare(b.startDateTime) || a.taskId - b.taskId,
  );

  return items;
}

function toDatePart(dateTime: string): string {
  return dateTime.slice(0, 10);
}

export function itemCoversDate(item: TimelineItem, date: string): boolean {
  const itemStart = toDatePart(item.startDateTime);
  const itemEnd = toDatePart(item.endDateTime);
  return itemStart <= date && date <= itemEnd;
}

export function getItemsForDate(items: TimelineItem[], date: string): TimelineItem[] {
  return items.filter(item => itemCoversDate(item, date));
}

function itemKey(item: TimelineItem): string {
  return `${item.taskId}-${item.userId}-${item.startDateTime}-${item.endDateTime}`;
}

export function getWeekItems(
  items: TimelineItem[],
  week: TimelineWeek,
): TimelineItem[] {
  const seen = new Set<string>();
  const result: TimelineItem[] = [];

  for (const day of week.days) {
    for (const item of getItemsForDate(items, day.date)) {
      const key = itemKey(item);
      if (!seen.has(key)) {
        seen.add(key);
        result.push(item);
      }
    }
  }

  return result;
}

export interface WeekSummary {
  resources: { userId: number; userName: string }[];
  tasks: { taskId: number; taskTitle: string }[];
}

export function summarizeWeek(items: TimelineItem[], week: TimelineWeek): WeekSummary {
  const resourceIds: number[] = [];
  const resourceNames = new Map<number, string>();
  const taskIds: number[] = [];
  const taskTitles = new Map<number, string>();

  for (const item of getWeekItems(items, week)) {
    if (!resourceIds.includes(item.userId)) {
      resourceIds.push(item.userId);
      resourceNames.set(item.userId, item.userName);
    }
    if (!taskIds.includes(item.taskId)) {
      taskIds.push(item.taskId);
      taskTitles.set(item.taskId, item.taskTitle);
    }
  }

  return {
    resources: resourceIds.map(id => ({
      userId: id,
      userName: resourceNames.get(id) ?? `User #${id}`,
    })),
    tasks: taskIds.map(id => ({
      taskId: id,
      taskTitle: taskTitles.get(id) ?? `Task #${id}`,
    })),
  };
}

export function abbreviateTaskTitle(title: string): string {
  const words = (title ?? '')
    .trim()
    .split(/\s+/)
    .map(w => w.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, ''))
    .filter(Boolean);
  if (words.length <= 1) {
    return words[0] ?? '';
  }
  const longWord = words.slice(1).find(w => w.length > 3);
  return longWord ?? words[0];
}

export function getMonthRange(weeks: TimelineWeek[]): string {
  if (weeks.length === 0) return '';
  const firstDay = weeks[0].days[0].date;
  const lastDay = weeks[weeks.length - 1].days[6].date;

  const [fY, fM] = firstDay.split('-').map(Number);
  const [lY, lM] = lastDay.split('-').map(Number);

  const firstMonth = `${MONTH_NAMES[fM - 1]} ${fY}`;
  if (fY === lY && fM === lM) return firstMonth;
  if (fY === lY) return `${MONTH_NAMES[fM - 1]} – ${MONTH_NAMES[lM - 1]} ${fY}`;
  return `${MONTH_NAMES[fM - 1]} ${fY} – ${MONTH_NAMES[lM - 1]} ${lY}`;
}
