import type { EvalRun } from '@/api/generated/aigateway';
import {
  caseRows,
  formatMetric,
  formatMetricDelta,
  isRegression,
  metricNames,
  metricPasses,
  previousRunOf,
  thresholdText,
} from './evals';

const run = (id: string, promptKey: string, version: number, ranAt: string): EvalRun => ({
  id,
  promptKey,
  promptVersion: version,
  suite: 'writing-email',
  mode: 'FULL',
  passed: true,
  metrics: {},
  ranAt: new Date(ranAt),
  receivedAt: new Date(ranAt),
});

describe('eval thresholds', () => {
  it('checks a metric against a minimum, a maximum or both', () => {
    expect(metricPasses(0.8, { min: 0.75 })).toBe(true);
    expect(metricPasses(0.7, { min: 0.75 })).toBe(false);
    expect(metricPasses(0.62, { max: 0.6 })).toBe(false);
    expect(metricPasses(0.5, { min: 0.2, max: 0.8 })).toBe(true);
    expect(metricPasses(0.5, undefined)).toBeNull();
    expect(metricPasses(0.5, { min: null, max: null })).toBeNull();
  });

  it('describes thresholds and deltas', () => {
    expect(thresholdText({ min: 0.75 })).toBe('≥ 0.75');
    expect(thresholdText({ max: 0.6 })).toBe('≤ 0.6');
    expect(thresholdText({ min: 0.2, max: 0.8 })).toBe('0.2–0.8');
    expect(thresholdText(undefined)).toBe('');
    expect(formatMetric(0.8123)).toBe('0.812');
    expect(formatMetric(12)).toBe('12');
    expect(formatMetricDelta(0.92, 0.9)).toBe('+0.02');
    expect(formatMetricDelta(0.5, 0.6)).toBe('−0.1');
    expect(formatMetricDelta(0.5, 0.5)).toBe('±0');
    expect(formatMetricDelta(0.5, undefined)).toBeNull();
  });

  it('orders thresholded metrics first', () => {
    expect(metricNames({ metrics: { b: 1, mae: 0.4, a: 2 }, thresholds: { mae: { max: 0.6 } } })).toEqual([
      'mae',
      'a',
      'b',
    ]);
  });
});

describe('previous run and regressions', () => {
  it('compares with the latest earlier run of the same prompt', () => {
    const runs = [
      run('3', 'writing.grade', 3, '2026-11-17T10:00:00Z'),
      run('x', 'other.prompt', 1, '2026-11-16T10:00:00Z'),
      run('2', 'writing.grade', 2, '2026-11-15T10:00:00Z'),
      run('1', 'writing.grade', 1, '2026-11-10T10:00:00Z'),
    ];
    expect(previousRunOf(runs, runs[0]!)?.id).toBe('2');
    expect(previousRunOf(runs, runs[3]!)).toBeUndefined();
  });

  it('flags a drop of a point or more, or a score more than one point from gold', () => {
    expect(isRegression({ gold: 4, predicted: 3 }, 4)).toBe(true);
    expect(isRegression({ gold: 4, predicted: 3.5 }, 4)).toBe(false);
    expect(isRegression({ gold: 4, predicted: 2.5 })).toBe(true);
    expect(isRegression({ gold: null, predicted: 2.5 })).toBe(false);
    const rows = caseRows(
      [
        { caseId: 'a', split: 'test', gold: 4, predicted: 3 },
        { caseId: 'b', split: 'test', gold: 3, predicted: 3 },
      ],
      [{ caseId: 'a', split: 'test', gold: 4, predicted: 4 }],
    );
    expect(rows.map((row) => [row.caseId, row.previousPredicted, row.regression])).toEqual([
      ['a', 4, true],
      ['b', undefined, false],
    ]);
  });
});
