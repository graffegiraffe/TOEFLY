import { createFileRoute, notFound } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ItemSetsPanel } from '@/components/admin/item-sets-panel';
import { ReportsPanel } from '@/components/admin/reports-panel';
import { SourcesPanel } from '@/components/admin/sources-panel';
import { UnitsPanel } from '@/components/admin/units-panel';
import { cn } from '@/lib/cn';

type Tab = 'itemSets' | 'units' | 'reports' | 'sources';
const TABS: Tab[] = ['itemSets', 'units', 'reports', 'sources'];

/** Content administration (docs/02-functional-requirements.md, admin of the item bank); ADMIN only. */
export const Route = createFileRoute('/_auth/_app/admin/content')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('ADMIN')) {
      throw notFound();
    }
  },
  component: AdminContent,
});

function AdminContent() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('itemSets');
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('admin.title')}</h1>
        <p className="text-body text-ink-muted">{t('admin.subtitle')}</p>
      </header>
      <div role="tablist" aria-label={t('admin.title')} className="flex gap-1 border-b border-border">
        {TABS.map((id) => (
          <button
            key={id}
            role="tab"
            id={`tab-${id}`}
            type="button"
            aria-selected={tab === id}
            aria-controls={`panel-${id}`}
            onClick={() => setTab(id)}
            className={cn(
              '-mb-px border-b-2 px-4 py-2 text-body font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
              tab === id ? 'border-brand-accent text-ink' : 'border-transparent text-ink-muted hover:text-ink',
            )}
          >
            {t(`admin.tabs.${id}`)}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === 'itemSets' && <ItemSetsPanel />}
        {tab === 'units' && <UnitsPanel />}
        {tab === 'reports' && <ReportsPanel />}
        {tab === 'sources' && <SourcesPanel />}
      </div>
    </div>
  );
}
