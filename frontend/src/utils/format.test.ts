import { describe, expect, it } from 'vitest';
import {
  formatDateTime,
  formatDuration,
  toDateTimeInputValue,
} from './format';

describe('formatDateTime', () => {
  it('formats an ISO timestamp as YYYY-MM-DD HH:mm', () => {
    expect(formatDateTime('2026-08-21T09:05:00')).toBe('2026-08-21 09:05');
  });

  it('returns a dash for null or undefined', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime(undefined)).toBe('—');
  });

  it('returns the raw value when it cannot be parsed', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date');
  });
});

describe('formatDuration', () => {
  it('renders minutes below one hour', () => {
    expect(formatDuration(45)).toBe('45m');
  });

  it('renders whole hours', () => {
    expect(formatDuration(120)).toBe('2h');
  });

  it('renders mixed hours and minutes', () => {
    expect(formatDuration(135)).toBe('2h 15m');
  });
});

describe('toDateTimeInputValue', () => {
  it('trims ISO strings to the datetime-local format', () => {
    expect(toDateTimeInputValue('2026-08-21T09:30:00')).toBe(
      '2026-08-21T09:30',
    );
  });

  it('returns an empty string for missing values', () => {
    expect(toDateTimeInputValue(null)).toBe('');
  });
});
