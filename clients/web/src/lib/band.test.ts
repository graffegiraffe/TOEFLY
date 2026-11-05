import { bandHint, cefrOf, comparableScore, isBand, overallOf } from './band';

describe('band scale', () => {
  it('AC-02.01.1 shows CEFR and the comparable 0–120 score next to the target', () => {
    expect(bandHint(6)).toBe('6.0 ≈ C2 ≈ 114+');
    expect(bandHint(5)).toBe('5.0 ≈ C1 ≈ 95+');
    expect(bandHint(4)).toBe('4.0 ≈ B2 ≈ 72+');
    expect(bandHint(3)).toBe('3.0 ≈ B1 ≈ 44+');
    expect(cefrOf(4.5)).toBe('B2');
    expect(cefrOf(1.5)).toBe('A1');
    expect(cefrOf(2)).toBe('A2');
    expect(comparableScore(3.5)).toBe(58);
  });

  it('AC-02.01.2 the overall target is the mean of sections rounded to 0.5', () => {
    expect(overallOf([5, 5, 4.5, 4])).toBe(4.5);
    expect(overallOf([4, 4, 4, 4.5])).toBe(4);
    expect(overallOf([6, 6, 5, 5])).toBe(5.5);
  });

  it('accepts only steps of 0.5 between 1 and 6', () => {
    expect(isBand(4.5)).toBe(true);
    expect(isBand(4.25)).toBe(false);
    expect(isBand(0.5)).toBe(false);
    expect(() => comparableScore(4.25)).toThrow(RangeError);
  });
});
