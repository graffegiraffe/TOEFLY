import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { aiProvidersQuery } from '@/api/ai';
import type { ProviderStatus, QuotaDimensionStatus } from '@/api/generated/aigateway';
import { EmptyState } from '@/components/ui/empty-state';
import { ProgressBar } from '@/components/ui/progress-bar';
import { humanize } from '@/lib/admin-content';
import { CIRCUIT_TONE, formatNumber, quotaLevel, quotaPercent, type Tone } from '@/lib/admin-ai';
import { ToneBadge } from './tone-badge';

const LEVEL_TONE: Record<ReturnType<typeof quotaLevel>, Tone> = { ok: 'success', low: 'warning', empty: 'danger' };

/** «AI → Providers»: free-tier quotas and the circuit breaker of every provider model, refreshed every 30 s. */
export function AiProvidersPanel() {
  const { t } = useTranslation();
  const { data, isError } = useQuery(aiProvidersQuery);
  return (
    <div className="flex flex-col gap-4">
      {isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}
      {data && data.items.length === 0 && <EmptyState title={t('adminAi.providers.empty')} />}
      <ul className="grid gap-3 md:grid-cols-2">
        {data?.items.map((status) => (
          <ProviderCard key={`${status.provider}-${status.model}`} status={status} />
        ))}
      </ul>
    </div>
  );
}

function ProviderCard({ status }: { status: ProviderStatus }) {
  const { t } = useTranslation();
  const headingId = `provider-${status.provider}-${status.model}`.replace(/[^A-Za-z0-9_-]/g, '-');
  return (
    <li aria-labelledby={headingId} className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 id={headingId} className="font-semibold text-ink">
            {humanize(status.provider)}
          </h3>
          <p className="break-all text-caption text-ink-muted">{status.model}</p>
        </div>
        <ToneBadge tone={CIRCUIT_TONE[status.circuit]}>{t(`adminAi.providers.circuit.${status.circuit}`)}</ToneBadge>
      </div>
      {status.dimensions.length === 0 ? (
        <p className="text-caption text-ink-muted">{t('adminAi.providers.noQuotas')}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {status.dimensions.map((dimension) => (
            <Quota key={dimension.dimension} dimension={dimension} />
          ))}
        </ul>
      )}
    </li>
  );
}

function Quota({ dimension }: { dimension: QuotaDimensionStatus }) {
  const { t } = useTranslation();
  const name = t(`adminAi.providers.dimension.${dimension.dimension}`);
  const level = quotaLevel(dimension);
  return (
    <li className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center justify-between gap-2 text-caption">
        <span className="text-ink">{name}</span>
        <span className="flex items-center gap-2">
          <span className="text-ink-muted">
            {t('adminAi.providers.remaining', {
              remaining: formatNumber(Math.max(0, dimension.remaining)),
              capacity: formatNumber(dimension.capacity),
            })}
          </span>
          {level !== 'ok' && <ToneBadge tone={LEVEL_TONE[level]}>{t(`adminAi.providers.level.${level}`)}</ToneBadge>}
        </span>
      </div>
      <ProgressBar
        value={Math.round(quotaPercent(dimension))}
        label={t('adminAi.providers.quota', { dimension: name })}
      />
    </li>
  );
}
