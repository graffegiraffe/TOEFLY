import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminSagasQuery, adminSubscriptionsQuery, useRetrySaga, type AdminSubscriptionFilter } from '@/api/billing';
import {
  PaymentProviderCode,
  PlanTierCode,
  SagaStatus,
  SagaStepStatusEnum,
  SubscriptionStatus,
  type SagaInstance,
} from '@/api/generated/billing';
import { ToneBadge } from '@/components/admin/tone-badge';
import { headClass, tableClass } from '@/components/progress/chart-frame';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import type { Tone } from '@/lib/admin-ai';
import { planNameKey } from '@/lib/billing';
import { formatDate } from '@/lib/dates';

const SAGA_TONE: Record<SagaStatus, Tone> = {
  [SagaStatus.Running]: 'accent',
  [SagaStatus.Compensating]: 'warning',
  [SagaStatus.Completed]: 'success',
  [SagaStatus.Failed]: 'danger',
};

const STEP_TONE: Record<SagaStepStatusEnum, Tone> = {
  [SagaStepStatusEnum.Pending]: 'neutral',
  [SagaStepStatusEnum.Done]: 'success',
  [SagaStepStatusEnum.Failed]: 'danger',
  [SagaStepStatusEnum.Skipped]: 'neutral',
  [SagaStepStatusEnum.Compensated]: 'warning',
};

function Select<T extends string>({
  label,
  value,
  options,
  render,
  onChange,
}: {
  label: string;
  value: T | undefined;
  options: T[];
  render: (option: T) => string;
  onChange: (value: T | undefined) => void;
}) {
  const { t } = useTranslation();
  return (
    <label className="flex flex-col gap-1 text-caption font-semibold text-ink-muted">
      {label}
      <select
        className="h-10 rounded-lg border border-border bg-surface px-3 text-body font-normal text-ink"
        value={value ?? ''}
        onChange={(event) => onChange((event.target.value || undefined) as T | undefined)}
      >
        <option value="">{t('adminBilling.subscriptions.all')}</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {render(option)}
          </option>
        ))}
      </select>
    </label>
  );
}

/** Subscriptions with filters and cursor paging (docs/05 §6.12 /admin/subscriptions). */
export function AdminSubscriptions() {
  const { t } = useTranslation();
  const [filter, setFilter] = useState<AdminSubscriptionFilter>({});
  const [cursors, setCursors] = useState<string[]>([]);
  const cursor = cursors.at(-1);
  const page = useQuery(adminSubscriptionsQuery({ ...filter, cursor }));
  const update = (patch: Partial<AdminSubscriptionFilter>) => {
    setFilter((current) => ({ ...current, ...patch }));
    setCursors([]);
  };
  return (
    <Card className="flex flex-col gap-4 overflow-x-auto" data-testid="admin-subscriptions">
      <h2 className="text-title-3 font-semibold text-ink">{t('adminBilling.subscriptions.title')}</h2>
      <div className="flex flex-wrap gap-3">
        <Select
          label={t('adminBilling.subscriptions.status')}
          value={filter.status}
          options={Object.values(SubscriptionStatus)}
          render={(status) => t(`billing.status.${status}`)}
          onChange={(status) => update({ status })}
        />
        <Select
          label={t('adminBilling.subscriptions.tier')}
          value={filter.tier}
          options={Object.values(PlanTierCode).filter((code) => code !== PlanTierCode.StudentFree)}
          render={(tier) => t(planNameKey(tier))}
          onChange={(tier) => update({ tier })}
        />
        <Select
          label={t('adminBilling.subscriptions.provider')}
          value={filter.provider}
          options={Object.values(PaymentProviderCode)}
          render={(provider) => t(`billing.providers.${provider}`)}
          onChange={(provider) => update({ provider })}
        />
      </div>
      {page.data && page.data.items.length === 0 && (
        <p className="text-body text-ink-muted">{t('adminBilling.subscriptions.empty')}</p>
      )}
      {page.data && page.data.items.length > 0 && (
        <table className={tableClass}>
          <thead>
            <tr className={headClass}>
              <th scope="col">{t('adminBilling.subscriptions.user')}</th>
              <th scope="col">{t('adminBilling.subscriptions.tier')}</th>
              <th scope="col">{t('adminBilling.subscriptions.status')}</th>
              <th scope="col">{t('adminBilling.subscriptions.provider')}</th>
              <th scope="col">{t('adminBilling.subscriptions.periodEnd')}</th>
            </tr>
          </thead>
          <tbody>
            {page.data.items.map((row) => (
              <tr key={row.id} className="border-t border-border">
                <td className="font-mono text-caption">{row.userId.slice(0, 8)}</td>
                <td>{t(planNameKey(row.planTierCode))}</td>
                <td>
                  {t(`billing.status.${row.status}`)}
                  {row.cancelAtPeriodEnd && (
                    <span className="block text-caption text-ink-muted">
                      {t('adminBilling.subscriptions.cancelAtEnd')}
                    </span>
                  )}
                </td>
                <td>{row.provider ? t(`billing.providers.${row.provider}`) : '—'}</td>
                <td>{formatDate(row.currentPeriodEnd ?? row.trialEndsAt ?? row.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="flex gap-2">
        {cursors.length > 0 && (
          <Button variant="ghost" onClick={() => setCursors([])}>
            {t('adminBilling.subscriptions.first')}
          </Button>
        )}
        {page.data?.nextCursor && (
          <Button variant="secondary" onClick={() => setCursors((list) => [...list, page.data!.nextCursor!])}>
            {t('adminBilling.subscriptions.next')}
          </Button>
        )}
      </div>
    </Card>
  );
}

function SagaRow({ saga }: { saga: SagaInstance }) {
  const { t } = useTranslation();
  const retry = useRetrySaga();
  const stuck = saga.status === SagaStatus.Running || saga.status === SagaStatus.Compensating;
  return (
    <li className="flex flex-col gap-2 border-t border-border py-3" data-testid={`saga-${saga.id}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-col">
          <span className="font-semibold text-ink">
            {saga.sagaType} · {t(planNameKey(saga.planTierCode))} · {t(`billing.providers.${saga.provider}`)}
          </span>
          <span className="text-caption text-ink-muted">
            {saga.userId.slice(0, 8)} · {t('adminBilling.sagas.updated', { date: formatDate(saga.updatedAt) })}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <ToneBadge tone={SAGA_TONE[saga.status]}>{t(`adminBilling.sagas.statuses.${saga.status}`)}</ToneBadge>
          {stuck && (
            <Button variant="secondary" onClick={() => retry.mutate(saga.id)} disabled={retry.isPending}>
              {retry.isSuccess ? t('adminBilling.sagas.retried') : t('adminBilling.sagas.retry')}
            </Button>
          )}
        </div>
      </div>
      <ol className="flex flex-col gap-1 text-caption">
        {saga.steps.map((step) => (
          <li key={step.stepNo} className="flex flex-wrap items-center gap-2">
            <ToneBadge tone={STEP_TONE[step.status]}>{t(`adminBilling.sagas.stepStatuses.${step.status}`)}</ToneBadge>
            <span className="text-ink">{t('adminBilling.sagas.step', { no: step.stepNo, name: step.name })}</span>
            {step.attempts > 1 && (
              <span className="text-ink-muted">{t('adminBilling.sagas.attempts', { count: step.attempts })}</span>
            )}
            {step.nextAttemptAt && (
              <span className="text-ink-muted">
                {t('adminBilling.sagas.nextAttempt', { date: step.nextAttemptAt.toLocaleString() })}
              </span>
            )}
            {step.lastError && <span className="break-all text-danger">{step.lastError}</span>}
          </li>
        ))}
      </ol>
    </li>
  );
}

/** Payment saga instances with their steps and a retry of a stuck step (docs/05 §6.12 /admin/sagas). */
export function AdminSagas() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<SagaStatus | undefined>();
  const sagas = useQuery(adminSagasQuery(status));
  return (
    <Card className="flex flex-col gap-3" data-testid="admin-sagas">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-title-3 font-semibold text-ink">{t('adminBilling.sagas.title')}</h2>
        <Select
          label={t('adminBilling.subscriptions.status')}
          value={status}
          options={Object.values(SagaStatus)}
          render={(value) => t(`adminBilling.sagas.statuses.${value}`)}
          onChange={setStatus}
        />
      </div>
      {sagas.data && sagas.data.items.length === 0 && (
        <p className="text-body text-ink-muted">{t('adminBilling.sagas.empty')}</p>
      )}
      <ul>
        {sagas.data?.items.map((saga) => (
          <SagaRow key={saga.id} saga={saga} />
        ))}
      </ul>
    </Card>
  );
}
