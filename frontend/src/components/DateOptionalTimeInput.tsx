interface DateOptionalTimeInputProps {
  id: string;
  timeLabel: string;
  dateValue: string;
  timeValue: string;
  onDateChange: (value: string) => void;
  onTimeChange: (value: string) => void;
  disabled?: boolean;
}

export function DateOptionalTimeInput({
  id,
  timeLabel,
  dateValue,
  timeValue,
  onDateChange,
  onTimeChange,
  disabled = false,
}: DateOptionalTimeInputProps) {
  return (
    <div className="input-with-action">
      <input
        id={id}
        type="date"
        value={dateValue}
        onChange={(e) => onDateChange(e.target.value)}
        disabled={disabled}
      />
      <input
        aria-label={timeLabel}
        type="time"
        value={timeValue}
        onChange={(e) => onTimeChange(e.target.value)}
        disabled={disabled}
      />
    </div>
  );
}
