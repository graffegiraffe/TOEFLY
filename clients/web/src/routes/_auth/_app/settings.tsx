import { createFileRoute, Link, Outlet } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { settingsTabsFor } from '@/lib/settings';

export const Route = createFileRoute('/_auth/_app/settings')({
  component: SettingsLayout,
});

/** Two columns on lg+: tabs on the left, the form on the right; a list above the form on small screens. */
function SettingsLayout() {
  const { t } = useTranslation();
  const { session } = Route.useRouteContext();
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('settings.title')}</h1>
      <div className="flex flex-col gap-6 lg:flex-row">
        <nav aria-label={t('settings.tabsLabel')} className="flex flex-row flex-wrap gap-1 lg:w-48 lg:flex-col">
          {settingsTabsFor(session.user.roles).map((tab) => (
            <Link
              key={tab}
              to={`/settings/${tab}`}
              className="rounded-[10px] px-3 py-2 text-[14px] font-medium text-ink-muted hover:bg-surface-sunken hover:text-ink"
              activeProps={{ className: '!bg-accent-soft !font-semibold !text-accent-on-soft', 'aria-current': 'page' }}
            >
              {t(`settings.tabs.${tab}`)}
            </Link>
          ))}
        </nav>
        <div className="min-w-0 max-w-[720px] flex-1">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
