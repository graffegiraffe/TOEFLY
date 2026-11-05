// Band scale of the TOEFL iBT 2026 with CEFR and the comparable 0–120 score (docs/03-domain-model.md §8.5).

export const BANDS = [1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6] as const;

export type Cefr = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';

const COMPARABLE_LOWER_BOUND: Record<number, number> = {
  6: 114,
  5.5: 105,
  5: 95,
  4.5: 84,
  4: 72,
  3.5: 58,
  3: 44,
  2.5: 33,
  2: 22,
  1.5: 11,
  1: 0,
};

export function isBand(value: number): boolean {
  return value >= 1 && value <= 6 && Number.isInteger(value * 2);
}

export function cefrOf(band: number): Cefr {
  if (band >= 6) return 'C2';
  if (band >= 5) return 'C1';
  if (band >= 4) return 'B2';
  if (band >= 3) return 'B1';
  if (band >= 2) return 'A2';
  return 'A1';
}

/** Lower bound of the comparable 0–120 score, shown as "95+" (reference until January 2028). */
export function comparableScore(band: number): number {
  const score = COMPARABLE_LOWER_BOUND[band];
  if (score === undefined) {
    throw new RangeError(`Not a band: ${band}`);
  }
  return score;
}

/** Mean of the section targets rounded half up to 0.5 (AC-02.01.2). */
export function overallOf(sections: readonly number[]): number {
  const mean = sections.reduce((sum, value) => sum + value, 0) / sections.length;
  return Math.floor(mean * 2 + 0.5) / 2;
}

export function formatBand(band: number): string {
  return band.toFixed(1);
}

/** "5.0 ≈ C1 ≈ 95+" (docs/07-ux-ui.md §5.3). */
export function bandHint(band: number): string {
  return `${formatBand(band)} ≈ ${cefrOf(band)} ≈ ${comparableScore(band)}+`;
}
