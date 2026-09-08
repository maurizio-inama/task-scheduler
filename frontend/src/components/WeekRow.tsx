import DayCell from './DayCell';
import type { TimelineWeek, TimelineItem } from '../utils/timeline';

interface WeekRowProps {
  week: TimelineWeek;
  items: TimelineItem[];
}

export default function WeekRow({ week, items }: WeekRowProps) {
  return (
    <div className="week-row">
      {week.days.map(day => (
        <DayCell key={day.date} day={day} items={items} />
      ))}
    </div>
  );
}
