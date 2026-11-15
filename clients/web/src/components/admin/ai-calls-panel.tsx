import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { aiCallsQuery, aiPromptsQuery } from '@/api/ai';
import { AiCallOutcome, AiProvider, type AiCall } from '@/api/generated/aigateway';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { humanize } from '@/lib/admin-content';
import { formatNumber, formatUsd, OUTCOME_TONE, promptLabel, type CallFilters } from '@/lib/admin-ai';
import { AiCallDialog } from './ai-call-dialog';
import { ToneBadge } from './tone-badge';

const selectClass =
  'h-10 rounded-lg border border-border bg-surface px-3 text-body text-ink focus-visible:outline-2 focus-visible:outline-focus-ring';

const COLUMNS = [
  'time',
  'useCase',
  'prompt',
  'model',
  'fallbackDepth',
  'degraded',
  'cacheHit',
  'tokens',
  'latency',
  'cost',
  'outcome',
] as const;

/** «AI → Calls»: the call log, newest first, with masked texts on demand. */
export function AiCallsPanel() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<CallFilters>({});
  const [viewing, setViewing] = useState<AiCall | null>(null);
  const query = useInfiniteQuery(aiCallsQuery(filters));
  const prompts = useQuery(aiPromptsQuery);
  const rows = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <form
        className="flex flex-wrap items-end gap-3"
        aria-label={t('adminAi.tabs.calls')}
        onSubmit={(event) => event.preventDefault()}
      >
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.calls.filters.promptKey')}
          <select
            className={selectClass}
            value={filters.promptKey ?? ''}
            onChange={(e) => setFilters({ ...filters, promptKey: e.target.value || undefined })}
          >
            <option value="">{t('adminAi.all')}</option>
            {prompts.data?.items.map((prompt) => (
              <option key={prompt.key} value={prompt.key}>
                {prompt.key}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.calls.filters.provider')}
          <select
            className={selectClass}
            value={filters.provider ?? ''}
            onChange={(e) =>
              setFilters({ ...filters, provider: (e.target.value || undefined) as AiProvider | undefined })
            }
          >
            <option value="">{t('adminAi.all')}</option>
            {Object.values(AiProvider).map((provider) => (
              <option key={provider} value={provider}>
                {humanize(provider)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.calls.filters.outcome')}
          <select
            className={selectClass}
            value={filters.outcome ?? ''}
            onChange={(e) =>
              setFilters({ ...filters, outcome: (e.target.value || undefined) as AiCallOutcome | undefined })
            }
          >
            <option value="">{t('adminAi.all')}</option>
            {Object.values(AiCallOutcome).map((outcome) => (
              <option key={outcome} value={outcome}>
                {t(`adminAi.calls.outcome.${outcome}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.calls.filters.from')}
          <input
            type="date"
            className={selectClass}
            value={filters.from ?? ''}
            onChange={(e) => setFilters({ ...filters, from: e.target.value || undefined })}
          />
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.calls.filters.to')}
          <input
            type="date"
            className={selectClass}
            value={filters.to ?? ''}
            onChange={(e) => setFilters({ ...filters, to: e.target.value || undefined })}
          />
        </label>
      </form>

      {query.isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}
      {!query.isPending && rows.length === 0 ? (
        <EmptyState title={t('adminAi.calls.empty')} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[1080px] border-collapse text-left text-caption">
            <caption className="sr-only">{t('adminAi.tabs.calls')}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {COLUMNS.map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`adminAi.calls.columns.${column}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((call) => (
                <CallRow key={call.id} call={call} onView={() => setViewing(call)} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {query.hasNextPage && (
        <div className="flex justify-center">
          <Button variant="secondary" onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {t('adminAi.loadMore')}
          </Button>
        </div>
      )}
      <AiCallDialog call={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}

function CallRow({ call, onView }: { call: AiCall; onView: () => void }) {
  const { t } = useTranslation();
  const time = call.createdAt.toLocaleString();
  return (
    <tr className="cursor-pointer border-t border-border align-top hover:bg-surface-sunken" onClick={onView}>
      <td className="px-3 py-2">
        <button
          type="button"
          className="text-left font-semibold text-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-focus-ring"
          aria-label={t('adminAi.calls.details', { time })}
          onClick={(event) => {
            event.stopPropagation();
            onView();
          }}
        >
          <time dateTime={call.createdAt.toISOString()}>{time}</time>
        </button>
      </td>
      <td className="px-3 py-2">{humanize(call.useCase)}</td>
      <td className="px-3 py-2 break-all">{promptLabel(call)}</td>
      <td className="px-3 py-2">
        <div>{humanize(call.provider)}</div>
        <div className="text-ink-muted">{call.model}</div>
      </td>
      <td className="px-3 py-2">{call.fallbackDepth}</td>
      <td className="px-3 py-2">{call.degraded ? t('adminAi.calls.yes') : t('adminAi.calls.no')}</td>
      <td className="px-3 py-2">{call.cacheHit ? t('adminAi.calls.hit') : t('adminAi.calls.miss')}</td>
      <td className="px-3 py-2 whitespace-nowrap">
        {t('adminAi.calls.tokens', { input: formatNumber(call.inputTokens), output: formatNumber(call.outputTokens) })}
      </td>
      <td className="px-3 py-2 whitespace-nowrap">
        {t('adminAi.calls.latency', { ms: formatNumber(call.latencyMs) })}
      </td>
      <td className="px-3 py-2 whitespace-nowrap">{formatUsd(call.shadowCostMicroUsd)}</td>
      <td className="px-3 py-2">
        <ToneBadge tone={OUTCOME_TONE[call.outcome]}>{t(`adminAi.calls.outcome.${call.outcome}`)}</ToneBadge>
      </td>
    </tr>
  );
}
