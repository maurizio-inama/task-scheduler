interface TimelineHeaderProps {
  periodLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
}

export default function TimelineHeader({
  periodLabel,
  onPrevious,
  onNext,
  onToday,
}: TimelineHeaderProps) {
  return (
    <div className="timeline-header">
      <span className="timeline-period">{periodLabel}</span>
      <div className="timeline-nav-group">
        <button
          type="button"
          className="timeline-nav-btn"
          aria-label="Previous week"
          title="Previous week"
          onClick={onPrevious}
        >
          ‹
        </button>
        <button
          type="button"
          className="timeline-nav-btn"
          aria-label="Go to current week"
          title="Go to current week"
          onClick={onToday}
        >
          Today
        </button>
        <button
          type="button"
          className="timeline-nav-btn"
          aria-label="Next week"
          title="Next week"
          onClick={onNext}
        >
          ›
        </button>
      </div>
    </div>
  );
}
