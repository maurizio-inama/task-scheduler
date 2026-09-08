import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, afterEach, vi } from 'vitest';
import DayCell from './DayCell';
import type { TimelineDay, TimelineItem } from '../utils/timeline';

const DAY: TimelineDay = {
  date: '2026-09-08',
  dayOfWeek: 1,
  isToday: false,
  dayLabel: 'Tue',
  monthIndex: 8,
  dayOfMonth: 8,
  year: 2026,
  showMonthYear: false,
  monthContextLabel: 'Sep',
};

function item(taskId: number, userId: number, start: string): TimelineItem {
  return {
    userId,
    userName: `user${userId}`,
    taskId,
    taskTitle: `Task number ${taskId}`,
    taskPriority: 'MEDIUM',
    startDateTime: start,
    endDateTime: `${start.slice(0, 10)}T17:00:00`,
  };
}

const FIVE_ITEMS = [
  item(1, 1, '2026-09-08T08:00:00'),
  item(2, 2, '2026-09-08T09:00:00'),
  item(3, 3, '2026-09-08T10:00:00'),
  item(4, 4, '2026-09-08T11:00:00'),
  item(5, 5, '2026-09-08T12:00:00'),
];

describe('DayCell', () => {
  it('renders up to three cards and a +N more indicator for the remainder', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    expect(document.querySelectorAll('.task-card')).toHaveLength(3);
    expect(screen.getByText('+2 more')).toBeTruthy();
  });

  it('shows no overflow indicator when content fits', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS.slice(0, 2)} />);

    expect(document.querySelectorAll('.task-card')).toHaveLength(2);
    expect(screen.queryByText(/\+.*more/)).toBeNull();
  });

  it('keeps the day empty for no tasks', () => {
    render(<DayCell day={DAY} items={[]} />);

    expect(document.querySelectorAll('.task-card')).toHaveLength(0);
    expect(screen.queryByText(/\+.*more/)).toBeNull();
    expect(screen.getByText('8')).toBeTruthy();
  });

  it('does not merge distinct tasks into fewer cards', () => {
    const distinct: TimelineItem[] = [
      {
        ...item(1, 1, '2026-09-08T08:00:00'),
        taskTitle: 'Database backup production',
      },
      {
        ...item(2, 2, '2026-09-08T09:00:00'),
        taskTitle: 'API testing suite',
      },
      {
        ...item(3, 3, '2026-09-08T10:00:00'),
        taskTitle: 'Deploy new endpoint',
      },
    ];
    render(<DayCell day={DAY} items={distinct} />);

    const cards = [...document.querySelectorAll('.task-card')];
    const labels = [...document.querySelectorAll('.task-card__label')].map(
      (el) => el.textContent,
    );
    expect(cards).toHaveLength(3);
    expect(labels).toEqual(['backup', 'testing', 'endpoint']);
    expect(new Set(labels).size).toBe(3);
  });
});

describe('DayCell overflow popover', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders +N more as an accessible button', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    const trigger = screen.getByRole('button', { name: '+2 more' });
    expect(trigger).toBeTruthy();
    expect(trigger.getAttribute('aria-haspopup')).toBe('dialog');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(trigger.getAttribute('aria-controls')).toBeTruthy();
    expect(trigger.getAttribute('title')).toBe('Show 2 more tasks');
  });

  it('opens a dialog listing the hidden tasks with full titles', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    fireEvent.click(screen.getByRole('button', { name: '+2 more' }));

    const popover = screen.getByRole('dialog', { name: '2 more tasks' });
    expect(popover).toBeTruthy();
    expect(popover.querySelectorAll('.popover-task .task-card')).toHaveLength(2);
    expect(screen.getByText('Task number 4')).toBeTruthy();
    expect(screen.getByText('Task number 5')).toBeTruthy();

    const trigger = screen.getByRole('button', { name: '+2 more' });
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(trigger.getAttribute('aria-controls')).toBe(popover.getAttribute('id'));
  });

  it('keeps the resource fill and task border distinction inside the popover', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);
    fireEvent.click(screen.getByRole('button', { name: '+2 more' }));

    const cards = [...document.querySelectorAll<HTMLElement>('.popover-task .task-card')];
    expect(cards).toHaveLength(2);
    cards.forEach((card) => {
      expect(card.style.backgroundColor).toBeTruthy();
      expect(card.style.borderLeftColor).toBeTruthy();
    });
    expect(cards[0].style.borderLeftColor).not.toBe(cards[1].style.borderLeftColor);
  });

  it('closes via the close button and resets aria-expanded', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    fireEvent.click(screen.getByRole('button', { name: '+2 more' }));
    expect(screen.getByRole('dialog')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(
      screen.getByRole('button', { name: '+2 more' }).getAttribute('aria-expanded'),
    ).toBe('false');
  });

  it('closes when Escape is pressed', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    fireEvent.click(screen.getByRole('button', { name: '+2 more' }));
    expect(screen.getByRole('dialog')).toBeTruthy();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('clamps the popover position inside the viewport', () => {
    Object.defineProperty(window, 'innerWidth', {
      value: 800,
      configurable: true,
      writable: true,
    });
    render(<DayCell day={DAY} items={FIVE_ITEMS} />);

    const trigger = screen.getByRole('button', { name: '+2 more' });
    (trigger as HTMLElement).getBoundingClientRect = () =>
      ({ left: 700, bottom: 120 } as DOMRect);

    fireEvent.click(trigger);

    const popover = screen.getByRole('dialog') as HTMLElement;
    expect(popover.style.left).toBe(`${800 - 250 - 8}px`);
    expect(popover.style.top).toBe('126px');
  });

  it('does not render a popover when all tasks fit', () => {
    render(<DayCell day={DAY} items={FIVE_ITEMS.slice(0, 3)} />);
    expect(screen.queryByRole('button', { name: /more/ })).toBeNull();
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});