import { getResourceColors, getTaskBorderColor } from '../utils/colors';

export interface TimelineLegendEntry {
  id: number;
  name: string;
  color: string;
}

interface TimelineLegendProps {
  resources: TimelineLegendEntry[];
  tasks: TimelineLegendEntry[];
}

const FILL_SAMPLE = getResourceColors(0).bg;
const BORDER_SAMPLE = getTaskBorderColor(0);

export default function TimelineLegend({ resources, tasks }: TimelineLegendProps) {
  return (
    <aside className="timeline-legend" aria-label="Timeline legend">
      <div className="legend-encoding">
        <span className="legend-key">
          <span
            className="legend-swatch legend-swatch--fill"
            style={{ backgroundColor: FILL_SAMPLE }}
          />
          Resource
        </span>
        <span className="legend-key">
          <span
            className="legend-swatch legend-swatch--border"
            style={{ borderColor: BORDER_SAMPLE }}
          />
          Task
        </span>
      </div>
      {resources.length > 0 && (
        <div className="legend-section">
          <h4 className="legend-title">Resources</h4>
          <ul className="legend-list">
            {resources.map(resource => (
              <li key={resource.id} className="legend-entry">
                <span
                  className="legend-swatch legend-swatch--fill"
                  style={{ backgroundColor: resource.color }}
                />
                <span className="legend-entry-name">{resource.name}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {tasks.length > 0 && (
        <div className="legend-section">
          <h4 className="legend-title">Tasks</h4>
          <ul className="legend-list">
            {tasks.map(task => (
              <li key={task.id} className="legend-entry">
                <span
                  className="legend-swatch legend-swatch--border"
                  style={{ borderColor: task.color }}
                />
                <span className="legend-entry-name">{task.name}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </aside>
  );
}