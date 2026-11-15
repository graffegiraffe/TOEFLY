import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { aiUsageQuery } from '@/api/ai';
import { EmptyState } from '@/components/ui/empty-state';
import { humanize } from '@/lib/admin-content';
import { costByProvider, defaultUsageRange, formatNumber, formatUsd, localDay, usageTotals } from '@/lib/admin-ai';
import { formatDate } from '@/lib/dates';

const inputClass =
  'h-10 rounded-lg border border-border bg-surface px-3 text-body text-ink focus-visible:outline-2 focus-visible:outline-focus-ring';

const COLUMNS = [
  'day',
  'provider',
  'model',
  'useCase',
  'calls',
  'cacheHits',
  'fallbacks',
  'tokens',
  'audio',
  'characters',
  'cost',
] as const;

/** «AI → Usage»: spending by day, provider, model and use case, with totals; cost in US dollars. */
export function AiUsagePanel({ today = new Date() }: { today?: Date }) {
  const { t } = useTranslation();
  const [range, setRange] = useState(() => defaultUsageRange(today));
  const invalid = Boolean(range.from && range.to && range.from > range.to);
  const { data, isError } = useQuery(aiUsageQuery(range.from, range.to));
  const rows = data?.rows ?? [];
  const totals = usageTotals(rows);
  const providers = costByProvider(rows);

  return (
    <div className="flex flex-col gap-4">
      <form
        className="flex flex-wrap items-end gap-3"
        aria-label={t('adminAi.tabs.usage')}
        onSubmit={(event) => event.preventDefault()}
      >
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.usage.from')}
          <input
            type="date"
            className={inputClass}
            value={range.from}
            max={range.to || undefined}
            onChange={(e) => setRange({ ...range, from: e.target.value })}
          />
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('adminAi.usage.to')}
          <input
            type="date"
            className={inputClass}
            value={range.to}
            min={range.from || undefined}
            onChange={(e) => setRange({ ...range, to: e.target.value })}
          />
        </label>
      </form>
      {invalid && (
        <p role="alert" className="text-danger">
          {t('adminAi.usage.invalidRange')}
        </p>
      )}
      {isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}

      {data && (
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {(
            [
              ['calls', formatNumber(totals.calls)],
              ['cacheHits', formatNumber(totals.cacheHits)],
              ['fallbacks', formatNumber(totals.fallbacks)],
              ['tokens', formatNumber(totals.inputTokens + totals.outputTokens)],
              ['cost', formatUsd(totals.shadowCostMicroUsd)],
            ] as const
          ).map(([key, value]) => (
            <div key={key} className="rounded-xl border border-border bg-surface p-4">
              <dt className="text-caption text-ink-muted">{t(`adminAi.usage.totals.${key}`)}</dt>
              <dd className="text-title-3 font-semibold text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      )}
      {providers.length > 0 && (
        <p className="text-caption text-ink">
          <span className="text-ink-muted">{t('adminAi.usage.byProvider')}: </span>
          {providers.map((p) => `${humanize(p.provider)} ${formatUsd(p.shadowCostMicroUsd)}`).join(' · ')}
        </p>
      )}
      {data && <p className="text-caption text-ink-muted">{t('adminAi.usage.shadowNote')}</p>}

      {data && rows.length === 0 ? (
        <EmptyState title={t('adminAi.usage.empty')} />
      ) : (
        rows.length > 0 && (
          <div className="overflow-x-auto rounded-xl border border-border">
            <table className="w-full min-w-[960px] border-collapse text-left text-caption">
              <caption className="sr-only">{t('adminAi.tabs.usage')}</caption>
              <thead className="bg-surface-sunken text-ink-muted">
                <tr>
                  {COLUMNS.map((column) => (
                    <th key={column} scope="col" className="px-3 py-2 font-semibold">
                      {t(`adminAi.usage.columns.${column}`)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={`${row.day.toISOString()}-${row.provider}-${row.model}-${row.useCase}-${row.feature ?? ''}`}
                    className="border-t border-border"
                  >
                    <td className="px-3 py-2 whitespace-nowrap">{formatDate(localDay(row.day))}</td>
                    <td className="px-3 py-2">{humanize(row.provider)}</td>
                    <td className="px-3 py-2">{row.model}</td>
                    <td className="px-3 py-2">{humanize(row.useCase)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.calls)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.cacheHits)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.fallbacks)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.inputTokens + row.outputTokens)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.audioSeconds)}</td>
                    <td className="px-3 py-2 text-right">{formatNumber(row.characters)}</td>
                    <td className="px-3 py-2 text-right whitespace-nowrap">{formatUsd(row.shadowCostMicroUsd)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t-2 border-border-strong font-semibold text-ink">
                <tr>
                  <th scope="row" colSpan={4} className="px-3 py-2">
                    {t('adminAi.usage.total')}
                  </th>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.calls)}</td>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.cacheHits)}</td>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.fallbacks)}</td>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.inputTokens + totals.outputTokens)}</td>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.audioSeconds)}</td>
                  <td className="px-3 py-2 text-right">{formatNumber(totals.characters)}</td>
                  <td className="px-3 py-2 text-right whitespace-nowrap">{formatUsd(totals.shadowCostMicroUsd)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )
      )}
    </div>
  );
}
