export const START_OF_DAY = '00:00';
export const END_OF_DAY = '23:59';

export interface SplitDateTime {
  date: string;
  time: string;
}

export function splitDateTime(value: string | null | undefined): SplitDateTime {
  if (!value || value.length < 16) {
    return { date: '', time: '' };
  }
  return { date: value.slice(0, 10), time: value.slice(11, 16) };
}

export function joinDateOptionalTime(
  date: string,
  time: string,
  defaultTime: string,
): string | null {
  return date ? `${date}T${time || defaultTime}` : null;
}
