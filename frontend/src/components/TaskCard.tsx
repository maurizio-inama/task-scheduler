interface TaskCardProps {
  label: string;
  resourceFillColor: string;
  taskBorderColor: string;
  title: string;
}

export default function TaskCard({
  label,
  resourceFillColor,
  taskBorderColor,
  title,
}: TaskCardProps) {
  return (
    <div
      className="task-card"
      style={{
        backgroundColor: resourceFillColor,
        borderLeftColor: taskBorderColor,
      }}
      title={title}
    >
      <span className="task-card__label">{label}</span>
    </div>
  );
}