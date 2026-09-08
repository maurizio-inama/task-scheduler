import { describe, expect, it } from 'vitest';
import { getResourceColors, getTaskBorderColor } from './colors';

describe('color palettes', () => {
  it('returns the same resource colors for the same user id', () => {
    expect(getResourceColors(7)).toEqual(getResourceColors(7));
    expect(getResourceColors(12)).toEqual(getResourceColors(12));
  });

  it('returns the same task border color for the same task id', () => {
    expect(getTaskBorderColor(3)).toBe(getTaskBorderColor(3));
    expect(getTaskBorderColor(14)).toBe(getTaskBorderColor(14));
  });

  it('distinguishes different resources', () => {
    expect(getResourceColors(1).bg).not.toBe(getResourceColors(2).bg);
  });

  it('gives every resource a border distinct from its fill', () => {
    for (let id = 1; id <= 10; id++) {
      const colors = getResourceColors(id);
      expect(colors.border).not.toBe(colors.bg);
    }
  });
});