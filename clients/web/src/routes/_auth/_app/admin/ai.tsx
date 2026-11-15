import { createFileRoute, notFound } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AiCallsPanel } from '@/components/admin/ai-calls-panel';
import { AiPromptsPanel } from '@/components/admin/ai-prompts-panel';
import { AiProvidersPanel } from '@/components/admin/ai-providers-panel';
import { AiUsagePanel } from '@/components/admin/ai-usage-panel';
import { cn } from '@/lib/cn';

type Tab = 'prompts' | 'calls' | 'usage' | 'providers';
const TABS: Tab[] = ['prompts', 'calls', 'usage', 'providers'];

/** AI administration of phase 5a (docs/12-implementation-plan.md, decision 10); ADMIN only. */
export const Route = createFileRoute('/_auth/_app/admin/ai')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('ADMIN')) {
      throw notFound();
    }
  },
  component: AdminAi,
});

function AdminAi() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('prompts');
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('adminAi.title')}</h1>
        <p className="text-body text-ink-muted">{t('adminAi.subtitle')}</p>
      </header>
      <div role="tablist" aria-label={t('adminAi.title')} className="flex gap-1 overflow-x-auto border-b border-border">
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
            {t(`adminAi.tabs.${id}`)}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === 'prompts' && <AiPromptsPanel />}
        {tab === 'calls' && <AiCallsPanel />}
        {tab === 'usage' && <AiUsagePanel />}
        {tab === 'providers' && <AiProvidersPanel />}
      </div>
    </div>
  );
}
