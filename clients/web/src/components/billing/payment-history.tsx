import { useTranslation } from 'react-i18next';
import { PaymentStatusEnum, type Payment } from '@/api/generated/billing';
import { ToneBadge } from '@/components/admin/tone-badge';
import { headClass, tableClass } from '@/components/progress/chart-frame';
import { Card } from '@/components/ui/card';
import type { Tone } from '@/lib/admin-ai';
import { formatPrice, planNameKey } from '@/lib/billing';
import { formatDate } from '@/lib/dates';

const TONE: Record<PaymentStatusEnum, Tone> = {
  [PaymentStatusEnum.Pending]: 'neutral',
  [PaymentStatusEnum.Succeeded]: 'success',
  [PaymentStatusEnum.Failed]: 'danger',
  [PaymentStatusEnum.Refunded]: 'warning',
};

/** Payment history with receipts (AC-27.05.1, US-27.07). */
export function PaymentHistory({ payments }: { payments: Payment[] }) {
  const { t } = useTranslation();
  return (
    <Card className="flex flex-col gap-3 overflow-x-auto" data-testid="payment-history">
      <h2 className="text-title-3 font-semibold text-ink">{t('billing.history.title')}</h2>
      {payments.length === 0 ? (
        <p className="text-body text-ink-muted">{t('billing.history.empty')}</p>
      ) : (
        <table className={tableClass}>
          <thead>
            <tr className={headClass}>
              <th scope="col">{t('billing.history.date')}</th>
              <th scope="col">{t('billing.history.plan')}</th>
              <th scope="col">{t('billing.history.amount')}</th>
              <th scope="col">{t('billing.history.status')}</th>
              <th scope="col">
                <span className="sr-only">{t('billing.history.receipt')}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {payments.map((payment) => (
              <tr key={payment.id} className="border-t border-border">
                <td>{formatDate(payment.paidAt ?? payment.createdAt)}</td>
                <td>
                  {t(planNameKey(payment.planTierCode))}
                  <span className="block text-caption text-ink-muted">
                    {t(`billing.providers.${payment.provider}`)}
                    {payment.testMode && ` · ${t('billing.history.test')}`}
                  </span>
                </td>
                <td className="whitespace-nowrap">
                  {payment.amountMinor != null && payment.currency
                    ? formatPrice(payment.amountMinor, payment.currency)
                    : '—'}
                </td>
                <td>
                  <ToneBadge tone={TONE[payment.status]}>{t(`billing.history.statuses.${payment.status}`)}</ToneBadge>
                </td>
                <td>
                  {payment.receiptUrl && (
                    <a href={payment.receiptUrl} target="_blank" rel="noreferrer" className="text-link underline">
                      {t('billing.history.receipt')}
                    </a>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
