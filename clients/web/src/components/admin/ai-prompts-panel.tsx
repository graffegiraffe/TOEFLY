import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { aiPromptsQuery, aiPromptVersionsQuery, useActivatePromptVersion } from '@/api/ai';
import type { PromptTemplate, PromptVersion } from '@/api/generated/aigateway';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { humanize } from '@/lib/admin-content';
import { isEvalsNotPassed, PROMPT_STATUS_TONE, versionAction } from '@/lib/admin-ai';
import { cn } from '@/lib/cn';
import { formatDate } from '@/lib/dates';
import { ToneBadge } from './tone-badge';

/** «AI → Prompts»: the registry, the versions of a prompt with their evals, activation and rollback. */
export function AiPromptsPanel() {
  const { t } = useTranslation();
  const { data, isError } = useQuery(aiPromptsQuery);
  const [selected, setSelected] = useState<string | null>(null);
  const prompts = data?.items ?? [];

  return (
    <div className="flex flex-col gap-6">
      {isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}
      {data && prompts.length === 0 ? (
        <EmptyState title={t('adminAi.prompts.empty')} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[720px] border-collapse text-left text-caption">
            <caption className="sr-only">{t('adminAi.tabs.prompts')}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {(['key', 'useCase', 'owner', 'active', 'canary'] as const).map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`adminAi.prompts.columns.${column}`)}
                  </th>
                ))}
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">{t('adminAi.prompts.open')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {prompts.map((prompt) => (
                <PromptRow
                  key={prompt.key}
                  prompt={prompt}
                  selected={selected === prompt.key}
                  onOpen={() => setSelected(prompt.key)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {selected ? (
        <VersionList promptKey={selected} />
      ) : (
        prompts.length > 0 && <p className="text-caption text-ink-muted">{t('adminAi.prompts.select')}</p>
      )}
    </div>
  );
}

function PromptRow({ prompt, selected, onOpen }: { prompt: PromptTemplate; selected: boolean; onOpen: () => void }) {
  const { t } = useTranslation();
  return (
    <tr className={cn('border-t border-border align-top', selected && 'bg-accent-soft/40')}>
      <td className="px-3 py-2">
        <div className="font-semibold text-ink">{prompt.key}</div>
        <div className="text-ink-muted">{prompt.description}</div>
      </td>
      <td className="px-3 py-2">{humanize(prompt.useCase)}</td>
      <td className="px-3 py-2">{prompt.ownerService}</td>
      <td className="px-3 py-2">{prompt.activeVersion ? `v${prompt.activeVersion}` : t('adminAi.prompts.none')}</td>
      <td className="px-3 py-2">
        {prompt.canaryVersion
          ? t('adminAi.prompts.canary', { version: prompt.canaryVersion, percent: prompt.canaryPercent })
          : t('adminAi.prompts.none')}
      </td>
      <td className="px-3 py-2">
        <Button
          variant={selected ? 'secondary' : 'ghost'}
          className="h-8 px-3 text-caption"
          aria-pressed={selected}
          aria-label={`${t('adminAi.prompts.open')}: ${prompt.key}`}
          onClick={onOpen}
        >
          {t('adminAi.prompts.open')}
        </Button>
      </td>
    </tr>
  );
}

function VersionList({ promptKey }: { promptKey: string }) {
  const { t } = useTranslation();
  const { data, isError } = useQuery(aiPromptVersionsQuery(promptKey));
  const headingId = `versions-${promptKey}`;
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <h2 id={headingId} className="text-title-3 font-semibold text-ink">
        {t('adminAi.prompts.versionsOf', { key: promptKey })}
      </h2>
      {isError && (
        <p role="alert" className="text-danger">
          {t('adminAi.error')}
        </p>
      )}
      {data && data.items.length === 0 && <EmptyState title={t('adminAi.prompts.versionsEmpty')} />}
      <ul className="flex flex-col gap-3">
        {data?.items.map((version) => (
          <VersionCard key={version.version} version={version} />
        ))}
      </ul>
    </section>
  );
}

function VersionCard({ version }: { version: PromptVersion }) {
  const { t } = useTranslation();
  const activate = useActivatePromptVersion();
  const action = versionAction(version);
  const isActive = action === null;
  const eval_ = version.latestEval;

  const run = () => {
    if (
      action === 'rollback' &&
      !window.confirm(t('adminAi.prompts.confirmRollback', { key: version.key, version: version.version }))
    ) {
      return;
    }
    activate.mutate({ promptKey: version.key, version: version.version });
  };

  return (
    <li
      aria-label={t('adminAi.prompts.version', { version: version.version })}
      className={cn('rounded-xl border bg-surface p-4', isActive ? 'border-brand-accent' : 'border-border')}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-semibold text-ink">{t('adminAi.prompts.version', { version: version.version })}</h3>
          <ToneBadge tone={PROMPT_STATUS_TONE[version.status]}>
            {t(`adminAi.prompts.status.${version.status}`)}
          </ToneBadge>
          {isActive && <span className="text-caption font-semibold text-ink">{t('adminAi.prompts.activeNow')}</span>}
        </div>
        <span className="text-caption text-ink-muted">
          {t('adminAi.prompts.createdBy', { who: version.createdBy, when: formatDate(version.createdAt) })}
        </span>
      </div>
      {version.description && <p className="mt-1 text-body text-ink">{version.description}</p>}
      <div className="mt-2 flex flex-wrap items-center gap-2 text-caption">
        <span className="text-ink-muted">{t('adminAi.prompts.eval.title')}:</span>
        {eval_ ? (
          <>
            <ToneBadge tone={eval_.passed ? 'success' : 'danger'}>
              {eval_.passed ? t('adminAi.prompts.eval.passed') : t('adminAi.prompts.eval.failed')}
            </ToneBadge>
            <span className="text-ink">
              {t('adminAi.prompts.eval.summary', {
                suite: eval_.suite,
                mode: eval_.mode,
                when: formatDate(eval_.ranAt),
              })}
            </span>
            {eval_.reportUrl && (
              <a
                className="text-link underline hover:text-link-hover"
                href={eval_.reportUrl}
                target="_blank"
                rel="noreferrer"
              >
                {t('adminAi.prompts.eval.report')}
              </a>
            )}
          </>
        ) : (
          <span className="text-ink-muted">{t('adminAi.prompts.eval.none')}</span>
        )}
      </div>
      {action && (
        <div className="mt-3">
          <Button
            variant={action === 'activate' ? 'secondary' : 'ghost'}
            className="h-8 px-3 text-caption"
            disabled={activate.isPending}
            onClick={run}
          >
            {t(`adminAi.prompts.${action}`)}
          </Button>
        </div>
      )}
      {activate.isSuccess && (
        <p role="status" className="mt-2 text-caption text-success">
          {t('adminAi.prompts.activated', { version: version.version })}
        </p>
      )}
      {activate.isError && (
        <p role="alert" className="mt-2 rounded-lg bg-danger-soft px-3 py-2 text-caption text-danger">
          {isEvalsNotPassed(activate.error)
            ? t('adminAi.prompts.evalsNotPassed', { version: version.version })
            : t('adminAi.error')}
        </p>
      )}
    </li>
  );
}
