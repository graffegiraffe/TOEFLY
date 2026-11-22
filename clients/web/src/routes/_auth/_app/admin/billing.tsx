import { createFileRoute, notFound } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { AdminSagas, AdminSubscriptions } from '@/components/billing/admin-billing-panel';

/** Subscriptions and payment sagas of phase 11a (docs/05-api-contracts.md §6.12); ADMIN only. */
export const Route = createFileRoute('/_auth/_app/admin/billing')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('ADMIN')) {
      throw notFound();
    }
  },
  component: AdminBilling,
});

function AdminBilling() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('adminBilling.title')}</h1>
        <p className="text-body text-ink-muted">{t('adminBilling.subtitle')}</p>
      </header>
      <AdminSubscriptions />
      <AdminSagas />
    </div>
  );
}
