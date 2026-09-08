import WeekRow from './WeekRow';
import type { TimelineWeek, TimelineItem } from '../utils/timeline';

interface TimelineGridProps {
  week: TimelineWeek;
  items: TimelineItem[];
}

export default function TimelineGrid({ week, items }: TimelineGridProps) {
  return (
    <div className="timeline-grid">
      <div className="timeline-day-headers">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(d => (
          <div key={d} className="timeline-day-header">{d}</div>
        ))}
      </div>
      <WeekRow week={week} items={items} />
    </div>
  );
}
