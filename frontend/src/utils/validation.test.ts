import { describe, expect, it } from 'vitest';
import {
  isBlank,
  isValidEmail,
  validateDateRange,
  validateEmailField,
  validatePositiveNumber,
  validateRequiredFields,
} from './validation';

describe('isBlank', () => {
  it('treats whitespace-only strings as blank', () => {
    expect(isBlank('   ')).toBe(true);
    expect(isBlank('x')).toBe(false);
  });
});

describe('isValidEmail', () => {
  it('accepts simple addresses', () => {
    expect(isValidEmail('user@example.com')).toBe(true);
  });

  it('rejects malformed addresses', () => {
    expect(isValidEmail('not-an-email')).toBe(false);
    expect(isValidEmail('a@b')).toBe(false);
  });
});

describe('validateRequiredFields', () => {
  it('flags only the missing fields', () => {
    const errors = validateRequiredFields(
      { title: 'Write docs', owner: '' },
      ['title', 'owner'],
    );
    expect(errors).toEqual({ owner: 'This field is required.' });
  });
});

describe('validateEmailField', () => {
  it('allows empty values (email is optional)', () => {
    expect(validateEmailField('')).toBeNull();
  });

  it('reports invalid addresses', () => {
    expect(validateEmailField('nope')).toBe('Enter a valid email address.');
  });
});

describe('validatePositiveNumber', () => {
  it('allows empty values', () => {
    expect(validatePositiveNumber('', 'Duration')).toBeNull();
  });

  it('rejects zero and negatives', () => {
    expect(validatePositiveNumber(0, 'Duration')).toContain('positive');
    expect(validatePositiveNumber(-5, 'Duration')).toContain('positive');
  });
});

describe('validateDateRange', () => {
  it('accepts end after start', () => {
    expect(validateDateRange('2026-01-01T08:00', '2026-01-01T17:00')).toBeNull();
  });

  it('rejects end before start', () => {
    expect(
      validateDateRange('2026-01-01T17:00', '2026-01-01T08:00'),
    ).toContain('after');
  });

  it('skips validation when either side is missing', () => {
    expect(validateDateRange('', '')).toBeNull();
  });
});
