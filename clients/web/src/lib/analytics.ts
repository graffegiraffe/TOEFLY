// Formatting and series of the analytics screens: Progress, the streak on Home and the admin metrics.

export const WEEK_SECTIONS = [
  'READING',
  'LISTENING',
  'WRITING',
  'SPEAKING',
  'GRAMMAR',
  'VOCABULARY',
  'RESOURCES',
] as const;

/** CSS color of a section in charts: the text color of its tag, readable in both themes (docs/07 §2.3). */
export function sectionColor(section: string): string {
  return `var(--tag-${section.toLowerCase()}-fg)`;
}

/** "45 m", "4 h 10 m", "0 m"; seconds are rounded to whole minutes. */
export function formatDuration(seconds: number): string {
  const minutes = Math.round(Math.max(0, seconds) / 60);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} m`;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} m`;
}

/** A calendar date of the API (UTC midnight) as `YYYY-MM-DD`. */
export function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date.getTime());
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

export interface DayBar {
  date: string;
  total: number;
  sections: { section: string; seconds: number }[];
  goalMet: boolean;
}

interface DayInput {
  date: Date;
  activeSeconds: number;
  goalMet: boolean;
  bySection: { section: string; activeSeconds: number }[];
}

/** One bar per day of `[from, to)`, days without activity included as empty bars. */
export function dailyBars(days: DayInput[], from: Date, to: Date): DayBar[] {
  const byDate = new Map(days.map((day) => [isoDate(day.date), day]));
  const bars: DayBar[] = [];
  for (let day = from; day < to; day = addDays(day, 1)) {
    const known = byDate.get(isoDate(day));
    bars.push({
      date: isoDate(day),
      total: known?.activeSeconds ?? 0,
      goalMet: known?.goalMet ?? false,
      sections: (known?.bySection ?? [])
        .filter((s) => s.activeSeconds > 0)
        .map((s) => ({ section: s.section, seconds: s.activeSeconds })),
    });
  }
  return bars;
}

export type PaceKind = 'slower' | 'faster' | 'onPace' | 'noNorm';

/** "You were 45% slower than the exam pace" (AC-16.02.2): the deviation in whole percent. */
export function pace(ratio: number | null | undefined, verdict: string): { kind: PaceKind; percent: number } {
  if (ratio === null || ratio === undefined || verdict === 'NO_NORM') return { kind: 'noNorm', percent: 0 };
  const deviation = Math.round(Math.abs(ratio - 1) * 100);
  if (verdict === 'SLOWER') return { kind: 'slower', percent: deviation };
  if (verdict === 'FASTER') return { kind: 'faster', percent: deviation };
  return { kind: 'onPace', percent: deviation };
}

export interface Freshness {
  key: 'never' | 'justNow' | 'minutes' | 'hours' | 'days';
  count: number;
}

/** How long ago the read model applied its last event (AC-05.02.3). */
export function freshness(asOf: Date | null | undefined, now: Date): Freshness {
  if (!asOf) return { key: 'never', count: 0 };
  const minutes = Math.floor((now.getTime() - asOf.getTime()) / 60_000);
  if (minutes < 1) return { key: 'justNow', count: 0 };
  if (minutes < 60) return { key: 'minutes', count: minutes };
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return { key: 'hours', count: hours };
  return { key: 'days', count: Math.floor(hours / 24) };
}

/** "45%" of a share 0–1, or "—" when there is nothing to divide. */
export function percent(share: number | null | undefined): string {
  return share === null || share === undefined ? '—' : `${Math.round(share * 100)}%`;
}

/** Shadow cost in micro-dollars as dollars with enough digits for free-tier traffic. */
export function formatUsd(microUsd: number | null | undefined): string {
  if (microUsd === null || microUsd === undefined) return '—';
  const usd = microUsd / 1_000_000;
  return usd >= 1 ? `$${usd.toFixed(2)}` : `$${usd.toFixed(4)}`;
}

export interface ChartPoint {
  x: number;
  y: number | null;
}

/** Values on `[min, max]` mapped to `[height, 0]`; `null` keeps the gap of a week without data. */
export function scale(
  values: (number | null | undefined)[],
  width: number,
  height: number,
  min: number,
  max: number,
): ChartPoint[] {
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  return values.map((value, index) => ({
    x: values.length > 1 ? index * step : width / 2,
    y: value === null || value === undefined ? null : height - ((value - min) / (max - min)) * height,
  }));
}

/** SVG path that breaks at missing points (AC-13.02.2). */
export function linePath(points: ChartPoint[]): string {
  let path = '';
  let drawing = false;
  for (const point of points) {
    if (point.y === null) {
      drawing = false;
      continue;
    }
    path += `${drawing ? 'L' : 'M'}${point.x.toFixed(1)},${point.y.toFixed(1)} `;
    drawing = true;
  }
  return path.trim();
}
