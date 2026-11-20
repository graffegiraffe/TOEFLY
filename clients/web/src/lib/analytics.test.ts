import {
  dailyBars,
  formatDuration,
  formatUsd,
  freshness,
  isoDate,
  linePath,
  pace,
  percent,
  scale,
  sectionColor,
} from './analytics';

const day = (value: string) => new Date(`${value}T00:00:00Z`);

describe('analytics formatting', () => {
  it('AC-16.02.1 durations in hours and minutes', () => {
    expect(formatDuration(0)).toBe('0 m');
    expect(formatDuration(2700)).toBe('45 m');
    expect(formatDuration(15_000)).toBe('4 h 10 m');
    expect(formatDuration(7200)).toBe('2 h');
    expect(formatDuration(-5)).toBe('0 m');
  });

  it('AC-16.02.2 the pace is the deviation from the exam norm in whole percent', () => {
    expect(pace(1.45, 'SLOWER')).toEqual({ kind: 'slower', percent: 45 });
    expect(pace(0.5, 'FASTER')).toEqual({ kind: 'faster', percent: 50 });
    expect(pace(1.1, 'ON_PACE')).toEqual({ kind: 'onPace', percent: 10 });
    expect(pace(null, 'NO_NORM')).toEqual({ kind: 'noNorm', percent: 0 });
  });

  it('AC-05.02.3 the freshness of the read model', () => {
    const now = new Date('2026-11-20T12:00:00Z');
    expect(freshness(null, now)).toEqual({ key: 'never', count: 0 });
    expect(freshness(new Date('2026-11-20T11:59:30Z'), now)).toEqual({ key: 'justNow', count: 0 });
    expect(freshness(new Date('2026-11-20T11:15:00Z'), now)).toEqual({ key: 'minutes', count: 45 });
    expect(freshness(new Date('2026-11-20T07:00:00Z'), now)).toEqual({ key: 'hours', count: 5 });
    expect(freshness(new Date('2026-11-17T12:00:00Z'), now)).toEqual({ key: 'days', count: 3 });
  });

  it('docs/01 §7 shares and shadow costs', () => {
    expect(percent(0.456)).toBe('46%');
    expect(percent(null)).toBe('—');
    expect(formatUsd(900)).toBe('$0.0009');
    expect(formatUsd(2_500_000)).toBe('$2.50');
    expect(formatUsd(undefined)).toBe('—');
  });

  it('section colors come from the tag tokens', () => {
    expect(sectionColor('READING')).toBe('var(--tag-reading-fg)');
  });
});

describe('analytics series', () => {
  it('AC-16.02.1 one bar per day, empty days included', () => {
    const bars = dailyBars(
      [
        {
          date: day('2026-11-18'),
          activeSeconds: 900,
          goalMet: true,
          bySection: [
            { section: 'READING', activeSeconds: 600 },
            { section: 'GRAMMAR', activeSeconds: 300 },
            { section: 'WRITING', activeSeconds: 0 },
          ],
        },
      ],
      day('2026-11-17'),
      day('2026-11-20'),
    );

    expect(bars.map((bar) => bar.date)).toEqual(['2026-11-17', '2026-11-18', '2026-11-19']);
    expect(bars[0]).toEqual({ date: '2026-11-17', total: 0, goalMet: false, sections: [] });
    expect(bars[1]?.sections).toEqual([
      { section: 'READING', seconds: 600 },
      { section: 'GRAMMAR', seconds: 300 },
    ]);
    expect(isoDate(day('2026-01-05'))).toBe('2026-01-05');
  });

  it('AC-13.02.1, AC-13.02.2 the band line breaks at weeks without an estimate', () => {
    const points = scale([4, null, 5, 5.5], 300, 100, 1, 6);

    expect(points[0]).toEqual({ x: 0, y: 40 });
    expect(points[1]?.y).toBeNull();
    expect(linePath(points)).toBe('M0.0,40.0 M200.0,20.0 L300.0,10.0');
    expect(scale([3], 300, 100, 1, 6)[0]?.x).toBe(150);
  });
});
