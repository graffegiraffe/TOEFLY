import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound, useNavigate } from '@tanstack/react-router';
import { ChevronLeft, Volume2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { entryQuery, useDeleteEntry, useEditEntry, vocabularySettingsQuery } from '@/api/vocabulary';
import type { VocabularyEntry } from '@/api/generated/vocabulary';
import { Modal } from '@/components/billing/modal';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { StabilityDots } from '@/components/vocabulary/stability-dots';
import { StatusBadge } from '@/components/vocabulary/status-badge';
import { stabilityLevel } from '@/lib/vocabulary';

/** Word detail (docs/07-ux-ui.md §5.14): IPA, definition, translation, examples, Edit, Suspend, Delete (US-15.06). */
export const Route = createFileRoute('/_auth/_app/vocabulary/$entryId')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: WordDetailPage,
});

function WordDetailPage() {
  const { t } = useTranslation();
  const { entryId } = Route.useParams();
  const entry = useQuery(entryQuery(entryId));
  if (entry.isPending) return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  if (entry.isError) {
    return (
      <EmptyState
        title={t('vocabulary.detail.notFound')}
        action={
          <Link to="/vocabulary" className="text-link hover:underline">
            {t('vocabulary.detail.back')}
          </Link>
        }
      />
    );
  }
  return <WordDetail entry={entry.data} />;
}

function WordDetail({ entry }: { entry: VocabularyEntry }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const settings = useQuery(vocabularySettingsQuery);
  const edit = useEditEntry(entry);
  const remove = useDeleteEntry();
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [translation, setTranslation] = useState(entry.data.translationRu ?? '');
  const [note, setNote] = useState(entry.note ?? '');
  const examples = [
    ...entry.contexts
      .slice()
      .reverse()
      .map((context) => ({ text: context.sentence, source: context.sourceLabel })),
    ...(entry.data.exampleEn ? [{ text: entry.data.exampleEn, source: null }] : []),
  ].slice(0, 2);
  const due = new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric' }).format(entry.card.due);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Link
        to="/vocabulary"
        className="inline-flex items-center gap-1 self-start text-body font-semibold text-link hover:text-link-hover hover:underline"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
        {t('vocabulary.detail.back')}
      </Link>
      <header className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-6 shadow-e1">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-display font-semibold tracking-tight text-ink">{entry.lemma}</h1>
          {settings.data?.pronunciationAvailable && (
            <Button variant="ghost" aria-label={t('vocabulary.detail.listen')}>
              <Volume2 className="size-5" aria-hidden="true" />
            </Button>
          )}
          <StatusBadge status={entry.status} />
          {entry.card.suspended && (
            <span className="text-caption font-semibold text-warning">{t('vocabulary.suspended')}</span>
          )}
        </div>
        <p className="text-body text-ink-muted">
          {[entry.data.partOfSpeech, entry.data.ipa].filter(Boolean).join(' · ')}
        </p>
        <div className="flex items-center gap-3 text-caption text-ink-muted">
          <StabilityDots level={stabilityLevel(entry.status, entry.card.stability)} />
          {entry.status !== 'NEW' && !entry.card.suspended && t('vocabulary.detail.nextReview', { date: due })}
        </div>
      </header>

      <section
        className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-6 shadow-e1"
        aria-labelledby="meaning"
      >
        <h2 id="meaning" className="text-title-3 font-semibold text-ink">
          {t('vocabulary.detail.meaning')}
        </h2>
        <p className="text-body-lg text-ink">{entry.data.definitionEn ?? t('vocabulary.definitionPending')}</p>
        {entry.data.translationRu && (
          <p className="text-body text-ink-muted" lang="ru" data-testid="translation">
            {entry.data.translationRu}
          </p>
        )}
        {entry.note && <p className="text-body text-ink">{t('vocabulary.detail.note', { note: entry.note })}</p>}
        {examples.length > 0 && (
          <>
            <h3 className="text-body font-semibold text-ink">{t('vocabulary.detail.examples')}</h3>
            <ul className="flex flex-col gap-2">
              {examples.map((example) => (
                <li key={example.text} className="text-body italic text-ink">
                  {example.text}
                  {example.source && (
                    <span className="block text-caption not-italic text-ink-muted">
                      {t('vocabulary.from', { source: example.source })}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      {editing && (
        <form
          className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-6 shadow-e1"
          onSubmit={(event) => {
            event.preventDefault();
            edit.mutate({ translationRu: translation, note }, { onSuccess: () => setEditing(false) });
          }}
        >
          <label className="flex flex-col gap-1 text-body font-semibold text-ink">
            {t('vocabulary.detail.translation')}
            <input
              value={translation}
              maxLength={200}
              lang="ru"
              onChange={(event) => setTranslation(event.target.value)}
              className="h-11 rounded-lg border border-border bg-surface px-3 font-normal focus-visible:outline-2 focus-visible:outline-focus-ring"
            />
          </label>
          <label className="flex flex-col gap-1 text-body font-semibold text-ink">
            {t('vocabulary.detail.noteLabel')}
            <textarea
              value={note}
              maxLength={500}
              rows={3}
              onChange={(event) => setNote(event.target.value)}
              className="rounded-lg border border-border bg-surface p-3 font-normal focus-visible:outline-2 focus-visible:outline-focus-ring"
            />
          </label>
          {edit.isError && (
            <p role="alert" className="text-caption text-danger">
              {t('vocabulary.detail.saveFailed')}
            </p>
          )}
          <div className="flex gap-2">
            <Button type="submit" disabled={edit.isPending}>
              {t('vocabulary.detail.save')}
            </Button>
            <Button variant="ghost" onClick={() => setEditing(false)}>
              {t('vocabulary.detail.cancel')}
            </Button>
          </div>
        </form>
      )}

      <div className="flex flex-wrap gap-3">
        {!editing && (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            {t('vocabulary.detail.edit')}
          </Button>
        )}
        <Button
          variant="secondary"
          disabled={edit.isPending}
          onClick={() => edit.mutate({ suspended: !entry.card.suspended })}
        >
          {entry.card.suspended ? t('vocabulary.detail.resume') : t('vocabulary.detail.suspend')}
        </Button>
        <Button variant="destructive" onClick={() => setConfirm(true)}>
          {t('vocabulary.detail.delete')}
        </Button>
      </div>

      <Modal open={confirm} onClose={() => setConfirm(false)} labelledBy="delete-title">
        <h2 id="delete-title" className="text-title-3 font-semibold text-ink">
          {t('vocabulary.detail.deleteTitle', { word: entry.lemma })}
        </h2>
        <p className="text-body text-ink-muted">{t('vocabulary.detail.deleteText')}</p>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirm(false)}>
            {t('vocabulary.detail.cancel')}
          </Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() =>
              remove.mutate(entry.id, { onSuccess: () => void navigate({ to: '/vocabulary', replace: true }) })
            }
          >
            {t('vocabulary.detail.delete')}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
