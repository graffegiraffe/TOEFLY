import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { aiUsageReportQuery, productMetricsQuery, rebuildJobQuery, useRebuildProjection } from '@/api/analytics';
import { ProjectionName, type AiBreakdown } from '@/api/generated/analytics';
import { headClass, tableClass } from '@/components/progress/chart-frame';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { formatUsd, percent } from '@/lib/analytics';

const PERIODS = [7, 30, 90] as const;

function startOf(days: number): Date {
  const today = new Date();
  const start = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
  start.setUTCDate(start.getUTCDate() - days + 1);
  return start;
}

function Stat({ label, value, hint, testId }: { label: string; value: string; hint?: string; testId: string }) {
  return (
    <Card className="flex flex-col gap-1 p-5" data-testid={testId}>
      <span className="text-caption font-semibold uppercase tracking-wide text-ink-muted">{label}</span>
      <span className="text-title-2 font-semibold text-ink">{value}</span>
      {hint && <span className="text-caption text-ink-muted">{hint}</span>}
    </Card>
  );
}

function BreakdownTable({ title, rows }: { title: string; rows: AiBreakdown[] }) {
  const { t } = useTranslation();
  return (
    <Card className="flex flex-col gap-3 overflow-x-auto" tabIndex={0}>
      <h3 className="text-title-3 font-semibold text-ink">{title}</h3>
      <table className={tableClass}>
        <thead>
          <tr className={headClass}>
            <th scope="col">{t('adminMetrics.ai.key')}</th>
            <th scope="col">{t('adminMetrics.ai.calls')}</th>
            <th scope="col">{t('adminMetrics.ai.tokens')}</th>
            <th scope="col">{t('adminMetrics.ai.cost')}</th>
            <th scope="col">{t('adminMetrics.ai.fallback')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="border-t border-border">
              <td className="font-semibold text-ink">{row.key}</td>
              <td>{row.totals.calls}</td>
              <td>
                {row.totals.tokensIn} / {row.totals.tokensOut}
              </td>
              <td>{formatUsd(row.totals.costMicroUsd)}</td>
              <td>{percent(row.totals.fallbackShare)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

function RebuildRow({ projection }: { projection: ProjectionName }) {
  const { t } = useTranslation();
  const rebuild = useRebuildProjection();
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useQuery({ ...rebuildJobQuery(jobId ?? ''), enabled: Boolean(jobId) });
  const status = job.data?.status;
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 border-t border-border py-3 first:border-t-0">
      <span className="font-semibold text-ink">{t(`adminMetrics.rebuild.projection.${projection}`)}</span>
      <span className="flex items-center gap-3">
        {status && (
          <span className="text-caption text-ink-muted" role="status">
            {t(`adminMetrics.rebuild.${status}`, {
              count: job.data?.eventsReplayed ?? 0,
              error: job.data?.error ?? '',
            })}
          </span>
        )}
        <Button
          variant="secondary"
          disabled={rebuild.isPending || status === 'RUNNING'}
          onClick={() => rebuild.mutate(projection, { onSuccess: (started) => setJobId(started.id) })}
        >
          <RotateCcw className="size-4" aria-hidden="true" />
          {t('adminMetrics.rebuild.start')}
        </Button>
      </span>
    </li>
  );
}

/** Product metrics (docs/01 §7), AI spend per learner and the rebuild of read models (ADR-0011). */
export function ProductMetricsPanel() {
  const { t } = useTranslation();
  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  const from = startOf(days);
  const product = useQuery(productMetricsQuery(from));
  const ai = useQuery(aiUsageReportQuery(from));
  if (product.isError || ai.isError) {
    return (
      <p role="alert" className="text-body text-danger">
        {t('adminMetrics.loadError')}
      </p>
    );
  }
  const funnel = product.data?.funnel;
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap gap-2" role="group" aria-label={t('adminMetrics.period')}>
        {PERIODS.map((value) => (
          <Button
            key={value}
            variant={value === days ? 'primary' : 'secondary'}
            aria-pressed={value === days}
            onClick={() => setDays(value)}
          >
            {t('adminMetrics.days', { count: value })}
          </Button>
        ))}
      </div>

      {product.data && funnel ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Stat
              testId="metric-sdr"
              label={t('adminMetrics.northStar')}
              value={percent(product.data.medianStudyDaysRatio)}
              hint={t('adminMetrics.northStarHint', { count: product.data.learnersInSdr })}
            />
            <Stat
              testId="metric-week-completion"
              label={t('adminMetrics.weekCompletion')}
              value={percent(product.data.weekCompletionRate)}
            />
            <Stat
              testId="metric-ai-per-learner"
              label={t('adminMetrics.ai.perLearner')}
              value={formatUsd(ai.data?.costPerActiveLearnerMicroUsd)}
            />
          </div>
          <Card className="flex flex-col gap-4" data-testid="funnel">
            <h2 className="text-title-3 font-semibold text-ink">{t('adminMetrics.funnel.title')}</h2>
            <ol className="grid gap-3 sm:grid-cols-5">
              {(['registered', 'onboarded', 'planCreated', 'firstTaskDone', 'activated'] as const).map((step) => (
                <li key={step} className="flex flex-col gap-1 rounded-lg bg-surface-sunken p-3">
                  <span className="text-caption text-ink-muted">{t(`adminMetrics.funnel.${step}`)}</span>
                  <span className="text-title-2 font-semibold text-ink">{funnel[step]}</span>
                  {step !== 'registered' && (
                    <span className="text-caption text-ink-muted">
                      {percent(funnel.registered ? funnel[step] / funnel.registered : null)}
                    </span>
                  )}
                </li>
              ))}
            </ol>
          </Card>
          <Card className="flex flex-col gap-3 overflow-x-auto" data-testid="retention" tabIndex={0}>
            <h2 className="text-title-3 font-semibold text-ink">{t('adminMetrics.retention')}</h2>
            <table className={tableClass}>
              <tbody>
                {product.data.retention.map((row) => (
                  <tr key={row.name} className="border-t border-border first:border-t-0">
                    <th scope="row" className="font-semibold text-ink">
                      {row.name}
                    </th>
                    <td>{percent(row.rate)}</td>
                    <td className="text-ink-muted">
                      {t('adminMetrics.retentionCell', { retained: row.retained, eligible: row.eligible })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </>
      ) : (
        <div className="h-40 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
      )}

      {ai.data && (
        <section className="flex flex-col gap-4" aria-labelledby="ai-spend">
          <h2 id="ai-spend" className="text-title-3 font-semibold text-ink">
            {t('adminMetrics.ai.title')}
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat testId="ai-calls" label={t('adminMetrics.ai.calls')} value={String(ai.data.totals.calls)} />
            <Stat testId="ai-cost" label={t('adminMetrics.ai.cost')} value={formatUsd(ai.data.totals.costMicroUsd)} />
            <Stat
              testId="ai-fallback"
              label={t('adminMetrics.ai.fallback')}
              value={percent(ai.data.totals.fallbackShare)}
            />
            <Stat testId="ai-cache" label={t('adminMetrics.ai.cacheHits')} value={String(ai.data.totals.cacheHits)} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <BreakdownTable title={t('adminMetrics.ai.byProvider')} rows={ai.data.byProvider} />
            <BreakdownTable title={t('adminMetrics.ai.byPrompt')} rows={ai.data.byPrompt} />
          </div>
          <BreakdownTable title={t('adminMetrics.ai.byModel')} rows={ai.data.byModel} />
        </section>
      )}

      <Card className="flex flex-col gap-2" data-testid="rebuild">
        <h2 className="text-title-3 font-semibold text-ink">{t('adminMetrics.rebuild.title')}</h2>
        <p className="text-body text-ink-muted">{t('adminMetrics.rebuild.text')}</p>
        <ul>
          {Object.values(ProjectionName).map((projection) => (
            <RebuildRow key={projection} projection={projection} />
          ))}
        </ul>
      </Card>
    </div>
  );
}
