import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  abbreviateTaskTitle,
  addDays,
  buildTimelineItems,
  buildWeek,
  buildWeeks,
  formatWeekRange,
  getItemsForDate,
  getMonthRange,
  getWeekItems,
  mondayOfWeek,
  summarizeWeek,
} from './timeline';
import type { TimelineItem } from './timeline';

describe('buildWeeks', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 31, 12, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts each week on Monday and ends on Sunday', () => {
    const weeks = buildWeeks(1, 1);

    expect(weeks).toHaveLength(3);
    expect(weeks[0].days[0].date).toBe('2026-08-24');
    expect(weeks[0].days[0].dayLabel).toBe('Mon');
    expect(weeks[0].days[6].date).toBe('2026-08-30');
    expect(weeks[0].days[6].dayLabel).toBe('Sun');
    expect(weeks[1].days[0].date).toBe('2026-08-31');
    expect(weeks[2].days[0].date).toBe('2026-09-07');
  });

  it('keeps a week intact across a month boundary', () => {
    const weeks = buildWeeks(0, 0);

    expect(weeks[0].days.map(d => d.date)).toEqual([
      '2026-08-31',
      '2026-09-01',
      '2026-09-02',
      '2026-09-03',
      '2026-09-04',
      '2026-09-05',
      '2026-09-06',
    ]);
  });

  it('marks only today', () => {
    const weeks = buildWeeks(0, 0);

    expect(weeks[0].days[0].isToday).toBe(true);
    expect(weeks[0].days.filter(d => d.isToday)).toHaveLength(1);
  });

  it('shows month/year context exactly where the month changes or it is today', () => {
    const days = buildWeeks(0, 0)[0].days;

    expect(days[0].monthContextLabel).toBe('Aug');
    expect(days[0].showMonthYear).toBe(true);
    expect(days[1].monthContextLabel).toBe('Sep 2026');
    expect(days[1].showMonthYear).toBe(true);
    expect(days[2].showMonthYear).toBe(false);
    expect(days[0].year).toBe(2026);
    expect(days[0].dayOfMonth).toBe(31);
    expect(days[1].dayOfMonth).toBe(1);
  });

  it('labels a year boundary across a continuous week', () => {
    vi.setSystemTime(new Date(2026, 11, 28, 12, 0, 0));
    const days = buildWeeks(0, 0)[0].days;

    expect(days[3].date).toBe('2026-12-31');
    expect(days[3].monthContextLabel).toBe('Dec');
    expect(days[3].showMonthYear).toBe(true);
    expect(days[4].date).toBe('2027-01-01');
    expect(days[4].monthContextLabel).toBe('Jan 2027');
    expect(days[4].year).toBe(2027);
    expect(days[4].showMonthYear).toBe(true);
  });
});

describe('abbreviateTaskTitle', () => {
  it.each([
    ['Database backup production', 'backup'],
    ['API test', 'test'],
    ['Fix UI', 'Fix'],
    ['Deployment', 'Deployment'],
  ])('%s → %s', (input, expected) => {
    expect(abbreviateTaskTitle(input)).toBe(expected);
  });

  it('handles empty, whitespace-only and null titles', () => {
    expect(abbreviateTaskTitle('')).toBe('');
    expect(abbreviateTaskTitle('   ')).toBe('');
    expect(abbreviateTaskTitle(null as unknown as string)).toBe('');
  });

  it('strips surrounding punctuation from candidate words', () => {
    expect(abbreviateTaskTitle('Deploy (prod)')).toBe('prod');
    expect(abbreviateTaskTitle('Fix UI!!')).toBe('Fix');
  });
});

describe('buildTimelineItems ordering', () => {
  const tasks = [
    {
      id: 1,
      title: 'Alpha',
      description: '',
      status: 'PENDING' as const,
      priority: 'MEDIUM' as const,
      estimatedDurationMinutes: 10,
      deadline: null,
      createdAt: '2026-01-01T00:00:00',
    },
    {
      id: 2,
      title: 'Beta',
      description: '',
      status: 'PENDING' as const,
      priority: 'MEDIUM' as const,
      estimatedDurationMinutes: 10,
      deadline: null,
      createdAt: '2026-01-01T00:00:00',
    },
  ];
  const users = [
    {
      id: 1,
      username: 'alice',
      firstName: 'A',
      lastName: 'B',
      email: 'a@x.io',
      role: 'OPERATOR' as const,
      enabled: true,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ];

  it('sorts by start date/time then task id', () => {
    const out = buildTimelineItems(
      [
        { id: 10, userId: 1, taskId: 1, scheduleId: 1, status: 'ASSIGNED' as const, startDateTime: '2026-09-10T08:00:00', endDateTime: '2026-09-10T10:00:00' },
        { id: 30, userId: 1, taskId: 1, scheduleId: 1, status: 'ASSIGNED' as const, startDateTime: '2026-09-10T09:00:00', endDateTime: '2026-09-10T11:00:00' },
        { id: 20, userId: 1, taskId: 2, scheduleId: 1, status: 'ASSIGNED' as const, startDateTime: '2026-09-10T08:00:00', endDateTime: '2026-09-10T09:00:00' },
      ],
      tasks,
      users,
    );

    expect(out.map(i => [i.taskId, i.startDateTime])).toEqual([
      [1, '2026-09-10T08:00:00'],
      [2, '2026-09-10T08:00:00'],
      [1, '2026-09-10T09:00:00'],
    ]);
  });

  it('excludes cancelled assignments', () => {
    const out = buildTimelineItems(
      [
        { id: 1, userId: 1, taskId: 1, scheduleId: 1, status: 'CANCELLED' as const, startDateTime: '2026-09-10T08:00:00', endDateTime: '2026-09-10T09:00:00' },
      ],
      tasks,
      users,
    );

    expect(out).toHaveLength(0);
  });
});

describe('getItemsForDate', () => {
  const itemA: TimelineItem = {
    userId: 1,
    userName: 'alice',
    taskId: 10,
    taskTitle: 'Database backup production',
    taskPriority: 'HIGH',
    startDateTime: '2026-09-02T08:00:00',
    endDateTime: '2026-09-04T17:00:00',
  };

  const itemB: TimelineItem = {
    userId: 2,
    userName: 'bob',
    taskId: 11,
    taskTitle: 'API test',
    taskPriority: 'MEDIUM',
    startDateTime: '2026-09-02T09:00:00',
    endDateTime: '2026-09-02T11:00:00',
  };

  it('includes a task on each day of its interval (inclusive end)', () => {
    expect(getItemsForDate([itemA, itemB], '2026-09-02').map(i => i.taskId)).toEqual([10, 11]);
    expect(getItemsForDate([itemA, itemB], '2026-09-03').map(i => i.taskId)).toEqual([10]);
    expect(getItemsForDate([itemA, itemB], '2026-09-04').map(i => i.taskId)).toEqual([10]);
  });

  it('excludes tasks outside the interval', () => {
    expect(getItemsForDate([itemA, itemB], '2026-09-01')).toEqual([]);
    expect(getItemsForDate([itemA, itemB], '2026-09-05')).toEqual([]);
  });
});

describe('getMonthRange', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 31, 12, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('describes a range spanning a single month', () => {
    vi.setSystemTime(new Date(2026, 7, 10, 12, 0, 0));
    expect(getMonthRange(buildWeeks(0, 0))).toBe('Aug 2026');
  });

  it('collapses same-year multi-month ranges', () => {
    expect(getMonthRange(buildWeeks(1, 1))).toBe('Aug – Sep 2026');
  });

  it('shows both years when the range crosses a year boundary', () => {
    expect(getMonthRange(buildWeeks(17, 17))).toBe('May 2026 – Jan 2027');
  });

  it('returns an empty string for no weeks', () => {
    expect(getMonthRange([])).toBe('');
  });
});

describe('getWeekItems', () => {
  const itemA: TimelineItem = {
    userId: 1,
    userName: 'alice',
    taskId: 10,
    taskTitle: 'Database backup production',
    taskPriority: 'HIGH',
    startDateTime: '2026-09-02T08:00:00',
    endDateTime: '2026-09-04T17:00:00',
  };

  const itemB: TimelineItem = {
    userId: 2,
    userName: 'bob',
    taskId: 11,
    taskTitle: 'API test',
    taskPriority: 'MEDIUM',
    startDateTime: '2026-09-02T09:00:00',
    endDateTime: '2026-09-02T11:00:00',
  };

  const week = buildWeek(new Date(2026, 8, 1));

  it('lists each task once even when it spans several days', () => {
    expect(getWeekItems([itemA, itemB], week).map(i => i.taskId)).toEqual([10, 11]);
  });

  it('returns an empty list for an empty week', () => {
    expect(getWeekItems([], week)).toEqual([]);
  });

  it('excludes items outside the week', () => {
    const farAway: TimelineItem = { ...itemB, startDateTime: '2026-10-01T09:00:00', endDateTime: '2026-10-01T11:00:00' };
    expect(getWeekItems([farAway], week)).toEqual([]);
  });
});

describe('summarizeWeek', () => {
  const itemA: TimelineItem = {
    userId: 1,
    userName: 'alice',
    taskId: 10,
    taskTitle: 'Database backup production',
    taskPriority: 'HIGH',
    startDateTime: '2026-09-02T08:00:00',
    endDateTime: '2026-09-04T17:00:00',
  };

  const itemB: TimelineItem = {
    userId: 1,
    userName: 'alice',
    taskId: 11,
    taskTitle: 'API test',
    taskPriority: 'MEDIUM',
    startDateTime: '2026-09-02T09:00:00',
    endDateTime: '2026-09-02T11:00:00',
  };

  const week = buildWeek(new Date(2026, 8, 1));

  it('collects unique resources and tasks preserving first-appearance order', () => {
    const summary = summarizeWeek([itemA, itemB], week);

    expect(summary.resources).toEqual([{ userId: 1, userName: 'alice' }]);
    expect(summary.tasks).toEqual([
      { taskId: 10, taskTitle: 'Database backup production' },
      { taskId: 11, taskTitle: 'API test' },
    ]);
  });

  it('destructures multi-task resources correctly for reordered items', () => {
    const otherUser: TimelineItem = { ...itemB, userId: 2, userName: 'bob' };
    const summary = summarizeWeek([otherUser, itemA], week);

    expect(summary.resources.map(r => r.userId)).toEqual([2, 1]);
    expect(summary.tasks.map(t => t.taskId)).toEqual([11, 10]);
  });

  it('returns empty sections for an empty week', () => {
    expect(summarizeWeek([], week)).toEqual({ resources: [], tasks: [] });
  });
});

describe('temporal navigation helpers', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  describe('mondayOfWeek', () => {
    it('returns midnight Monday for a mid-week date', () => {
      const monday = mondayOfWeek(new Date(2026, 8, 10));
      expect(monday.getDay()).toBe(1);
      expect(monday.getDate()).toBe(7);
      expect(monday.getHours()).toBe(0);
      expect(monday.getMinutes()).toBe(0);
    });

    it('returns the same date for a Monday', () => {
      const monday = mondayOfWeek(new Date(2026, 8, 7));
      expect(monday.getDate()).toBe(7);
    });

    it('returns the previous week for a Sunday', () => {
      const monday = mondayOfWeek(new Date(2026, 8, 13));
      expect(monday.getDate()).toBe(7);
    });

    it('normalizes an input containing a time component', () => {
      const monday = mondayOfWeek(new Date(2026, 8, 10, 23, 45, 0));
      expect(monday.getDate()).toBe(7);
      expect(monday.getHours()).toBe(0);
    });
  });

  describe('addDays', () => {
    it('adds a positive number of days', () => {
      expect(addDays(new Date(2026, 8, 7), 7).getDate()).toBe(14);
      expect(addDays(new Date(2026, 8, 7), 7).getMonth()).toBe(8);
    });

    it('subtracts days for negative values', () => {
      expect(addDays(new Date(2026, 8, 7), -7).getDate()).toBe(31);
      expect(addDays(new Date(2026, 8, 7), -7).getMonth()).toBe(7);
    });

    it('crosses a year boundary', () => {
      const d = addDays(new Date(2026, 11, 28), 7);
      expect(d.getFullYear()).toBe(2027);
      expect(d.getMonth()).toBe(0);
      expect(d.getDate()).toBe(4);
    });

    it('does not mutate the input date', () => {
      const input = new Date(2026, 8, 7);
      addDays(input, 7);
      expect(input.getDate()).toBe(7);
    });
  });

  describe('buildWeek', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 7, 31, 12, 0, 0));
    });

    it('builds one continuous Monday–Sunday week from a mid-week anchor', () => {
      const week = buildWeek(new Date(2026, 8, 8, 14, 30, 0));
      expect(week.days.map(d => d.date)).toEqual([
        '2026-09-07',
        '2026-09-08',
        '2026-09-09',
        '2026-09-10',
        '2026-09-11',
        '2026-09-12',
        '2026-09-13',
      ]);
    });

    it('exposes the month index on every day', () => {
      const week = buildWeek(new Date(2026, 8, 8, 14, 30, 0));
      expect(week.days[0].monthIndex).toBe(8);
      const boundary = buildWeek(new Date(2026, 7, 31, 12, 0, 0));
      expect(boundary.days[0].monthIndex).toBe(7);
      expect(boundary.days[1].monthIndex).toBe(8);
    });

    it('matches the day cells produced by buildWeeks', () => {
      const expected = buildWeeks(0, 0)[0];
      const single = buildWeek(new Date());
      expect(single).toEqual(expected);
    });
  });

  describe('formatWeekRange', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 7, 31, 12, 0, 0));
    });

    it('formats a single-month week with a full month name', () => {
      expect(formatWeekRange(buildWeek(new Date(2026, 8, 7)))).toBe('September 7 – 13, 2026');
    });

    it('formats a week crossing a month boundary within one year', () => {
      expect(formatWeekRange(buildWeek(new Date(2026, 7, 31)))).toBe('August 31 – September 6, 2026');
    });

    it('formats a week crossing a year boundary', () => {
      expect(formatWeekRange(buildWeek(new Date(2026, 11, 28)))).toBe('December 28, 2026 – January 3, 2027');
    });
  });

  describe('one-week navigation across a year boundary', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 11, 28, 12, 0, 0));
    });

    it('advances from the 28 Dec 2026 Monday week to the 4 Jan 2027 Monday week', () => {
      const anchor = mondayOfWeek(new Date(2026, 11, 28));
      expect(anchor.getDate()).toBe(28);

      const week = buildWeek(anchor);
      expect(week.days[0].date).toBe('2026-12-28');
      expect(week.days[6].date).toBe('2027-01-03');

      const nextWeek = buildWeek(addDays(anchor, 7));
      expect(nextWeek.days[0].date).toBe('2027-01-04');
      expect(nextWeek.days[6].date).toBe('2027-01-10');
    });

    it('moves back from the 4 Jan 2027 week to the 28 Dec 2026 week', () => {
      const anchor = mondayOfWeek(new Date(2027, 0, 5));
      expect(anchor.getDate()).toBe(4);

      const previousWeek = buildWeek(addDays(anchor, -7));
      expect(previousWeek.days[0].date).toBe('2026-12-28');
      expect(previousWeek.days[6].date).toBe('2027-01-03');
    });
  });
});