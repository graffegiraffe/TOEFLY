import { createFileRoute, notFound } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { ProductMetricsPanel } from '@/components/admin/product-metrics-panel';

/** Product metrics and AI spend of phase 11c (docs/12-implementation-plan.md); ADMIN only. */
export const Route = createFileRoute('/_auth/_app/admin/metrics')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('ADMIN')) {
      throw notFound();
    }
  },
  component: AdminMetrics,
});

function AdminMetrics() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('adminMetrics.title')}</h1>
        <p className="text-body text-ink-muted">{t('adminMetrics.subtitle')}</p>
      </header>
      <ProductMetricsPanel />
    </div>
  );
}
