// «AI → Evals» (docs/06-ai-agent.md §7.5, docs/12 phase 5b decision 15): metrics against thresholds, the delta to
// the previous run of the same prompt and the per-case diff with its regression filter.
import type { EvalCaseResult, EvalRun, EvalThreshold } from '@/api/generated/aigateway';

/** "≥ 0.80", "≤ 0.60", "0.20–0.80", or an empty string without a threshold. */
export function thresholdText(threshold: EvalThreshold | undefined): string {
  const min = threshold?.min ?? null;
  const max = threshold?.max ?? null;
  if (min !== null && max !== null) return `${formatMetric(min)}–${formatMetric(max)}`;
  if (min !== null) return `≥ ${formatMetric(min)}`;
  if (max !== null) return `≤ ${formatMetric(max)}`;
  return '';
}

/** Whether a metric is within its threshold; null when the metric has none. */
export function metricPasses(value: number, threshold: EvalThreshold | undefined): boolean | null {
  const min = threshold?.min ?? null;
  const max = threshold?.max ?? null;
  if (min === null && max === null) return null;
  return (min === null || value >= min) && (max === null || value <= max);
}

export function formatMetric(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(Math.abs(value) < 10 ? 3 : 1).replace(/\.?0+$/, '');
}

/** "+0.020", "−0.100"; null without a previous value. */
export function formatMetricDelta(value: number, previous: number | undefined): string | null {
  if (previous === undefined) return null;
  const delta = value - previous;
  if (Math.abs(delta) < 1e-9) return '±0';
  return `${delta > 0 ? '+' : '−'}${formatMetric(Math.abs(delta))}`;
}

/** The run before this one for the same prompt (any version), by the time it ran. */
export function previousRunOf(runs: readonly EvalRun[], run: EvalRun): EvalRun | undefined {
  return runs
    .filter((other) => other.promptKey === run.promptKey && other.id !== run.id && other.ranAt < run.ranAt)
    .sort((a, b) => b.ranAt.getTime() - a.ranAt.getTime())[0];
}

export interface CaseRow extends EvalCaseResult {
  previousPredicted?: number;
  regression: boolean;
}

/**
 * A regression: the score dropped by at least one point against the previous run, or it is more than one point
 * away from the gold score.
 */
export function isRegression(row: Pick<EvalCaseResult, 'gold' | 'predicted'>, previousPredicted?: number): boolean {
  const dropped = previousPredicted !== undefined && previousPredicted - row.predicted >= 1;
  const far = row.gold !== null && row.gold !== undefined && Math.abs(row.predicted - row.gold) > 1;
  return dropped || far;
}

export function caseRows(cases: readonly EvalCaseResult[], previous: readonly EvalCaseResult[] = []): CaseRow[] {
  const before = new Map(previous.map((item) => [item.caseId, item.predicted]));
  return cases.map((item) => {
    const previousPredicted = before.get(item.caseId);
    return { ...item, previousPredicted, regression: isRegression(item, previousPredicted) };
  });
}

/** Metric names in a stable order: thresholded ones first, then alphabetically. */
export function metricNames(run: Pick<EvalRun, 'metrics' | 'thresholds'>): string[] {
  const names = new Set([...Object.keys(run.metrics ?? {}), ...Object.keys(run.thresholds ?? {})]);
  return [...names].sort((a, b) => {
    const ta = run.thresholds?.[a] ? 0 : 1;
    const tb = run.thresholds?.[b] ? 0 : 1;
    return ta - tb || a.localeCompare(b);
  });
}
