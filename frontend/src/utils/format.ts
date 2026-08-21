export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

export function formatDate(value: string | null | undefined): string {
  return formatDateTime(value).slice(0, 10);
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest}m`;
  }
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

export function toDateTimeInputValue(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  return value.slice(0, 16);
}

export function fromDateTimeInputValue(value: string): string | null {
  return value.length > 0 ? value : null;
}
