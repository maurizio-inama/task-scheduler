import { describe, expect, it } from 'vitest';
import {
  END_OF_DAY,
  START_OF_DAY,
  joinDateOptionalTime,
  splitDateTime,
} from './datetime';

describe('splitDateTime', () => {
  it('splits an ISO datetime into date and time', () => {
    expect(splitDateTime('2026-09-20T14:30:00')).toEqual({
      date: '2026-09-20',
      time: '14:30',
    });
  });

  it('returns empty parts for null or short values', () => {
    expect(splitDateTime(null)).toEqual({ date: '', time: '' });
    expect(splitDateTime('2026-09-20')).toEqual({ date: '', time: '' });
  });
});

describe('joinDateOptionalTime', () => {
  it('uses the provided time when present', () => {
    expect(joinDateOptionalTime('2026-09-20', '08:15', END_OF_DAY)).toBe(
      '2026-09-20T08:15',
    );
  });

  it('falls back to the default time when the time is blank', () => {
    expect(joinDateOptionalTime('2026-09-20', '', START_OF_DAY)).toBe(
      '2026-09-20T00:00',
    );
    expect(joinDateOptionalTime('2026-09-21', '', END_OF_DAY)).toBe(
      '2026-09-21T23:59',
    );
  });

  it('returns null when no date is given', () => {
    expect(joinDateOptionalTime('', '08:15', START_OF_DAY)).toBeNull();
  });
});
