export type Errors<T> = Partial<Record<keyof T, string>>;

export function isBlank(value: string): boolean {
  return value.trim().length === 0;
}

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export function validateRequiredFields(
  values: Record<string, string>,
  fields: readonly string[],
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of fields) {
    if (isBlank(values[field] ?? '')) {
      errors[field] = 'This field is required.';
    }
  }
  return errors;
}

export function validateEmailField(
  email: string,
): string | null {
  if (isBlank(email)) {
    return null;
  }
  return isValidEmail(email) ? null : 'Enter a valid email address.';
}

export function validatePositiveNumber(
  value: number | '',
  label: string,
): string | null {
  if (value === '') {
    return null;
  }
  if (!Number.isFinite(value) || value <= 0) {
    return `${label} must be a positive number.`;
  }
  return null;
}

export function validateDateRange(
  start: string,
  end: string,
): string | null {
  if (isBlank(start) || isBlank(end)) {
    return null;
  }
  return start < end ? null : 'The end must be after the start.';
}
