import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { aiEvalRunQuery, aiEvalRunsQuery, aiPromptsQuery } from '@/api/ai';
import type { EvalRun } from '@/api/generated/aigateway';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { cn } from '@/lib/cn';
import { formatDate } from '@/lib/dates';
import {
  caseRows,
  formatMetric,
  formatMetricDelta,
  metricNames,
  metricPasses,
  previousRunOf,
  thresholdText,
} from '@/lib/evals';
import { ToneBadge } from './tone-badge';

/** «AI → Evals» (docs/06-ai-agent.md §7.5): eval runs, a run's metrics against thresholds and its per-case diff. */
export function AiEvalsPanel() {
  const { t } = useTranslation();
  const [promptKey, setPromptKey] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const prompts = useQuery(aiPromptsQuery);
  const runs = useQuery(aiEvalRunsQuery(promptKey));
  const items = runs.data?.items ?? [];
  const run = items.find((item) => item.id === selected);

  return (
    <div className="flex flex-col gap-6">
      <label className="flex max-w-xs flex-col gap-1 text-caption font-semibold text-ink">
        {t('adminAi.evals.prompt')}
        <select
          value={promptKey}
          onChange={(event) => {
            setPromptKey(event.target.value);
            setSelected(null);
          }}
          className="h-10 rounded-lg border border-input bg-surface px-3 text-body font-normal text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
        >
          <option value="">{t('adminAi.all')}</option>
          {(prompts.data?.items ?? []).map((prompt) => (
            <option key={prompt.key} value={prompt.key}>
              {prompt.key}
            </option>
          ))}
        </select>
      </label>
      {runs.isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}
      {runs.data && items.length === 0 ? (
        <EmptyState title={t('adminAi.evals.empty')} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[720px] border-collapse text-left text-caption">
            <caption className="sr-only">{t('adminAi.tabs.evals')}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {(['prompt', 'version', 'suite', 'mode', 'result', 'ranAt'] as const).map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`adminAi.evals.columns.${column}`)}
                  </th>
                ))}
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">{t('adminAi.evals.open')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className={cn('border-t border-border', selected === item.id && 'bg-accent-soft/40')}>
                  <td className="px-3 py-2 font-semibold text-ink">{item.promptKey}</td>
                  <td className="px-3 py-2">v{item.promptVersion}</td>
                  <td className="px-3 py-2">{item.suite}</td>
                  <td className="px-3 py-2">{item.mode}</td>
                  <td className="px-3 py-2">
                    <ToneBadge tone={item.passed ? 'success' : 'danger'}>
                      {item.passed ? t('adminAi.prompts.eval.passed') : t('adminAi.prompts.eval.failed')}
                    </ToneBadge>
                  </td>
                  <td className="px-3 py-2">{formatDate(item.ranAt)}</td>
                  <td className="px-3 py-2">
                    <Button
                      variant={selected === item.id ? 'secondary' : 'ghost'}
                      className="h-8 px-3 text-caption"
                      aria-pressed={selected === item.id}
                      aria-label={t('adminAi.evals.openRun', { key: item.promptKey, version: item.promptVersion })}
                      onClick={() => setSelected(item.id)}
                    >
                      {t('adminAi.evals.open')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {run ? (
        <RunDetail run={run} previous={previousRunOf(items, run)} />
      ) : (
        items.length > 0 && <p className="text-caption text-ink-muted">{t('adminAi.evals.select')}</p>
      )}
    </div>
  );
}

function RunDetail({ run, previous }: { run: EvalRun; previous: EvalRun | undefined }) {
  const { t } = useTranslation();
  const detail = useQuery(aiEvalRunQuery(run.id));
  const before = useQuery({ ...aiEvalRunQuery(previous?.id ?? 'none'), enabled: Boolean(previous) });
  const [regressionsOnly, setRegressionsOnly] = useState(false);
  const data = detail.data ?? run;
  const names = metricNames(data);
  const rows = caseRows(data.cases ?? [], before.data?.cases ?? []);
  const shown = regressionsOnly ? rows.filter((row) => row.regression) : rows;
  const regressions = rows.filter((row) => row.regression).length;

  return (
    <section aria-labelledby="run-title" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 id="run-title" className="text-title-3 font-semibold text-ink">
          {t('adminAi.evals.runTitle', { key: run.promptKey, version: run.promptVersion, suite: run.suite })}
        </h2>
        <p className="text-caption text-ink-muted">
          {previous
            ? t('adminAi.evals.comparedWith', { version: previous.promptVersion, when: formatDate(previous.ranAt) })
            : t('adminAi.evals.noPrevious')}
          {run.reportUrl && (
            <>
              {' · '}
              <a
                className="text-link underline hover:text-link-hover"
                href={run.reportUrl}
                target="_blank"
                rel="noreferrer"
              >
                {t('adminAi.prompts.eval.report')}
              </a>
            </>
          )}
        </p>
      </div>
      {detail.isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full min-w-[560px] border-collapse text-left text-caption">
          <caption className="sr-only">{t('adminAi.evals.metrics')}</caption>
          <thead className="bg-surface-sunken text-ink-muted">
            <tr>
              {(['metric', 'value', 'threshold', 'status', 'delta'] as const).map((column) => (
                <th key={column} scope="col" className="px-3 py-2 font-semibold">
                  {t(`adminAi.evals.metricColumns.${column}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {names.map((name) => {
              const value = data.metrics?.[name];
              const threshold = data.thresholds?.[name];
              const pass = value === undefined ? null : metricPasses(value, threshold);
              const delta = value === undefined ? null : formatMetricDelta(value, previous?.metrics?.[name]);
              return (
                <tr key={name} className="border-t border-border">
                  <th scope="row" className="px-3 py-2 font-semibold text-ink">
                    {name}
                  </th>
                  <td
                    className={cn(
                      'px-3 py-2 tabular-nums',
                      pass === false ? 'text-danger' : pass ? 'text-success' : 'text-ink',
                    )}
                  >
                    {value === undefined ? '—' : formatMetric(value)}
                  </td>
                  <td className="px-3 py-2 tabular-nums">{thresholdText(threshold) || '—'}</td>
                  <td className="px-3 py-2">
                    {pass === null ? (
                      <span className="text-ink-muted">—</span>
                    ) : (
                      <ToneBadge tone={pass ? 'success' : 'danger'}>
                        {pass ? t('adminAi.evals.pass') : t('adminAi.evals.fail')}
                      </ToneBadge>
                    )}
                  </td>
                  <td className="px-3 py-2 tabular-nums">{delta ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-body font-semibold text-ink">{t('adminAi.evals.cases', { count: rows.length })}</h3>
        <label className="inline-flex items-center gap-2 text-caption text-ink">
          <input
            type="checkbox"
            checked={regressionsOnly}
            onChange={(event) => setRegressionsOnly(event.target.checked)}
            className="size-4 accent-[var(--primary)]"
          />
          {t('adminAi.evals.regressionsOnly', { count: regressions })}
        </label>
      </div>
      {detail.isPending ? (
        <div className="h-32 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
      ) : rows.length === 0 ? (
        <p className="text-caption text-ink-muted">{t('adminAi.evals.noCases')}</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[640px] border-collapse text-left text-caption">
            <caption className="sr-only">{t('adminAi.evals.casesCaption')}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {(['caseId', 'split', 'gold', 'predicted', 'previous', 'passes', 'verdict'] as const).map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`adminAi.evals.caseColumns.${column}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {shown.map((row) => (
                <tr key={row.caseId} className="border-t border-border" data-testid="eval-case">
                  <th scope="row" className="px-3 py-2 font-mono font-normal text-ink">
                    {row.caseId}
                  </th>
                  <td className="px-3 py-2">{row.split}</td>
                  <td className="px-3 py-2 tabular-nums">{row.gold ?? '—'}</td>
                  <td className="px-3 py-2 tabular-nums">{row.predicted}</td>
                  <td className="px-3 py-2 tabular-nums">{row.previousPredicted ?? '—'}</td>
                  <td className="px-3 py-2 tabular-nums">{(row.passes ?? []).join(' · ') || '—'}</td>
                  <td className="px-3 py-2">
                    {row.regression ? (
                      <ToneBadge tone="danger">{t('adminAi.evals.regression')}</ToneBadge>
                    ) : (
                      <span className="text-ink-muted">{t('adminAi.evals.ok')}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
