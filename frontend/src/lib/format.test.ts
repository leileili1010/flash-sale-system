import { describe, expect, it } from 'vitest';
import { countdownParts, formatMoney, formatShortDuration } from './format';

describe('countdownParts', () => {
  it('splits a duration into padded hours, minutes and seconds', () => {
    expect(countdownParts((1 * 3600 + 2 * 60 + 3) * 1000)).toEqual({ hours: '01', minutes: '02', seconds: '03', done: false });
  });

  it('rounds partial seconds up so the timer never shows 00:00:00 early', () => {
    expect(countdownParts(400).seconds).toBe('01');
  });

  it('does not cap hours at a day', () => {
    expect(countdownParts(30 * 3600 * 1000).hours).toBe('30');
  });

  it('clamps past targets to zero', () => {
    expect(countdownParts(-5000)).toEqual({ hours: '00', minutes: '00', seconds: '00', done: true });
  });
});

describe('formatShortDuration', () => {
  it.each([
    [45_000, '45s'],
    [12 * 60_000, '12m'],
    [(3 * 60 + 48) * 60_000, '3h 48m'],
    [(26 * 60) * 60_000, '1d 2h'],
  ])('formats %d ms as %s', (ms, expected) => {
    expect(formatShortDuration(ms)).toBe(expected);
  });
});

describe('formatMoney', () => {
  it('formats US dollars', () => {
    expect(formatMoney(11.99)).toBe('$11.99');
    expect(formatMoney(119)).toBe('$119.00');
  });
});
