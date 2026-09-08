import { useEffect, useId, useRef, useState } from 'react';
import TaskCard from './TaskCard';
import type { TimelineDay, TimelineItem } from '../utils/timeline';
import {
  abbreviateTaskTitle,
  getItemsForDate,
} from '../utils/timeline';
import { getResourceColors, getTaskBorderColor } from '../utils/colors';

const MAX_VISIBLE_TASKS = 3;
const POPOVER_WIDTH = 250;
const VIEWPORT_MARGIN = 8;

interface DayCellProps {
  day: TimelineDay;
  items: TimelineItem[];
}

export default function DayCell({ day, items }: DayCellProps) {
  const dayItems = getItemsForDate(items, day.date);
  const visibleItems = dayItems.slice(0, MAX_VISIBLE_TASKS);
  const hiddenItems = dayItems.slice(MAX_VISIBLE_TASKS);
  const hiddenCount = hiddenItems.length;

  const triggerRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const popoverId = useId();
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number } | null>(null);

  useEffect(() => {
    if (!detailsOpen) return;

    function handlePointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (triggerRef.current?.contains(target) || popoverRef.current?.contains(target)) {
        return;
      }
      setDetailsOpen(false);
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setDetailsOpen(false);
      }
    }

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [detailsOpen]);

  useEffect(() => {
    if (detailsOpen) {
      popoverRef.current?.focus();
    }
  }, [detailsOpen]);

  function toggleDetails() {
    if (!detailsOpen) {
      const rect = triggerRef.current?.getBoundingClientRect();
      const viewportW = window.innerWidth;
      const viewportH = window.innerHeight;

      let left = rect?.left ?? 0;
      if (left + POPOVER_WIDTH + VIEWPORT_MARGIN > viewportW) {
        left = Math.max(VIEWPORT_MARGIN, viewportW - POPOVER_WIDTH - VIEWPORT_MARGIN);
      }

      const top = Math.min(
        (rect?.bottom ?? 0) + 6,
        Math.max(VIEWPORT_MARGIN, viewportH - 200),
      );

      setPopoverPos({ top, left });
      setDetailsOpen(true);
    } else {
      setDetailsOpen(false);
    }
  }

  return (
    <div className={`day-cell${day.isToday ? ' day-cell--today' : ''}`}>
      <div className="day-cell-header">
        <span
          className={`day-number${day.isToday ? ' day-number--today' : ''}`}
        >
          {day.dayOfMonth}
        </span>
        {day.showMonthYear && (
          <span className="day-month">{day.monthContextLabel}</span>
        )}
        {day.isToday && <span className="today-badge">Today</span>}
      </div>
      <div className="day-items">
        {visibleItems.map((item, index) => {
          const label = abbreviateTaskTitle(item.taskTitle);
          const resource = getResourceColors(item.userId);
          const taskBorder = getTaskBorderColor(item.taskId);
          return (
            <TaskCard
              key={`${item.taskId}-${item.userId}-${index}`}
              label={label}
              resourceFillColor={resource.bg}
              taskBorderColor={taskBorder}
              title={`${item.taskTitle} — ${item.userName}`}
            />
          );
        })}
        {hiddenCount > 0 && (
          <button
            type="button"
            ref={triggerRef}
            className="more-tasks"
            title={`Show ${hiddenCount} more ${hiddenCount === 1 ? 'task' : 'tasks'}`}
            aria-haspopup="dialog"
            aria-expanded={detailsOpen}
            aria-controls={popoverId}
            onClick={toggleDetails}
          >
            +{hiddenCount} more
          </button>
        )}
      </div>
      {detailsOpen && (
        <div
          id={popoverId}
          ref={popoverRef}
          className="timeline-popover"
          role="dialog"
          aria-label={`${hiddenCount} more ${hiddenCount === 1 ? 'task' : 'tasks'}`}
          tabIndex={-1}
          style={{
            top: `${popoverPos?.top ?? 0}px`,
            left: `${popoverPos?.left ?? 0}px`,
          }}
        >
          <div className="popover-header">
            <span>
              {hiddenCount} more {hiddenCount === 1 ? 'task' : 'tasks'}
            </span>
            <button
              type="button"
              className="popover-close"
              aria-label="Close"
              onClick={() => setDetailsOpen(false)}
            >
              ×
            </button>
          </div>
          <ul className="popover-list">
            {hiddenItems.map((item, index) => {
              const resource = getResourceColors(item.userId);
              const taskBorder = getTaskBorderColor(item.taskId);
              return (
                <li
                  key={`${item.taskId}-${item.userId}-${index}`}
                  className="popover-task"
                >
                  <TaskCard
                    label={item.taskTitle}
                    resourceFillColor={resource.bg}
                    taskBorderColor={taskBorder}
                    title={`${item.taskTitle} — ${item.userName}`}
                  />
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}