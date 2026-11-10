import { describe, expect, it } from 'vitest';
import { formatClock, paceDeviation, timerText } from './timer';

describe('US-06.01 practice timer', () => {
  it('AC-06.01.2: counts down to the norm, then shows overtime', () => {
    expect(timerText(180, 20)).toEqual({ text: '02:40', overtime: false });
    expect(timerText(180, 222)).toEqual({ text: '+00:42', overtime: true });
    expect(formatClock(-3)).toBe('00:00');
  });

  it('AC-16.02.2: only deviations above 30% are reported', () => {
    expect(paceDeviation(522, 360)).toBe(45);
    expect(paceDeviation(200, 360)).toBe(-44);
    expect(paceDeviation(380, 360)).toBeNull();
    expect(paceDeviation(0, 360)).toBeNull();
  });
});
