import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { BookmarkPlus, Check, X } from 'lucide-react';
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { lookupQuery, useSaveWord } from '@/api/vocabulary';
import { Button } from '@/components/ui/button';
import { selectionTerm, sentenceAround } from '@/lib/vocabulary';

interface Picked {
  term: string;
  context?: string;
  top: number;
  left: number;
}

interface WordLookupAreaProps {
  children: ReactNode;
  itemSetId?: string;
  sourceLabel?: string;
  /** AC-15.01.4: false in Exam mode — no card appears. */
  enabled?: boolean;
}

/**
 * AC-15.01.1 on the web: selecting a word (double click or a keyboard selection) inside the area opens its card with
 * the definition, part of speech, example, Russian translation and IPA, and "Save word".
 */
export function WordLookupArea({ children, itemSetId, sourceLabel, enabled = true }: WordLookupAreaProps) {
  const area = useRef<HTMLDivElement>(null);
  const [picked, setPicked] = useState<Picked | null>(null);

  const pick = useCallback(() => {
    if (!enabled) return;
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return;
    const anchor = selection.anchorNode;
    if (!anchor || !area.current?.contains(anchor)) return;
    const term = selectionTerm(selection.toString());
    if (!term) return;
    const block = (anchor instanceof Element ? anchor : anchor.parentElement)?.closest('p, li, dd, blockquote, td');
    const context = sentenceAround(block?.textContent ?? anchor.textContent ?? '', term);
    const rect = selection.getRangeAt(0).getBoundingClientRect();
    const width = Math.min(360, window.innerWidth - 32);
    setPicked({
      term,
      context,
      top: Math.min(rect.bottom + 8, window.innerHeight - 24),
      left: Math.max(16, Math.min(rect.left, window.innerWidth - width - 16)),
    });
  }, [enabled]);

  return (
    <div ref={area} onMouseUp={pick} onKeyUp={(event) => event.shiftKey && pick()} data-word-lookup="">
      {children}
      {picked && (
        <WordCardPopover
          key={`${picked.term}|${picked.context ?? ''}`}
          picked={picked}
          itemSetId={itemSetId}
          sourceLabel={sourceLabel}
          onClose={() => setPicked(null)}
        />
      )}
    </div>
  );
}

function WordCardPopover({
  picked,
  itemSetId,
  sourceLabel,
  onClose,
}: {
  picked: Picked;
  itemSetId?: string;
  sourceLabel?: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const card = useQuery(lookupQuery(picked.term, picked.context));
  const save = useSaveWord();

  useEffect(() => {
    panel.current?.focus();
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const lemma = card.data?.lemma ?? picked.term.toLowerCase();
  const saved = save.data;
  const already = saved?.alreadyInDictionary === true || (!saved && card.data?.entryId != null);
  const entryId = saved?.id ?? card.data?.entryId ?? undefined;

  return (
    <div
      ref={panel}
      role="dialog"
      aria-modal="false"
      aria-labelledby={titleId}
      tabIndex={-1}
      onMouseUp={(event) => event.stopPropagation()}
      style={{ top: picked.top, left: picked.left }}
      className="fixed z-50 flex w-[min(360px,calc(100vw-32px))] flex-col gap-3 rounded-xl border border-border bg-surface p-4 text-ink shadow-e1 focus-visible:outline-2 focus-visible:outline-focus-ring"
      data-testid="word-card"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 id={titleId} className="text-title-3 font-semibold text-ink">
            {lemma}
          </h2>
          <p className="text-caption text-ink-muted">
            {[card.data?.data.partOfSpeech, card.data?.data.ipa].filter(Boolean).join(' · ')}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={t('vocabulary.card.close')}
          className="rounded-md p-1 text-ink-muted hover:bg-surface-sunken focus-visible:outline-2 focus-visible:outline-focus-ring"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>
      {card.isPending && <div className="h-16 animate-pulse rounded-lg bg-surface-sunken" aria-busy="true" />}
      {card.data && card.data.available && (
        <div className="flex flex-col gap-1">
          <p className="text-body text-ink">{card.data.data.definitionEn}</p>
          {card.data.data.exampleEn && <p className="text-body italic text-ink-muted">{card.data.data.exampleEn}</p>}
          {card.data.data.translationRu && (
            <p className="text-body text-ink-muted" lang="ru">
              {card.data.data.translationRu}
            </p>
          )}
        </div>
      )}
      {(card.isError || (card.data && !card.data.available)) && (
        <p className="text-body text-ink-muted">{t('vocabulary.card.unavailable')}</p>
      )}
      {already ? (
        <p className="flex items-center gap-2 text-body font-semibold text-accent-on-soft" role="status">
          <Check className="size-4" aria-hidden="true" />
          {saved?.alreadyInDictionary === false ? t('vocabulary.card.saved') : t('vocabulary.card.already')}
          {entryId && (
            <Link to="/vocabulary/$entryId" params={{ entryId }} className="ml-auto text-link hover:underline">
              {t('vocabulary.card.open')}
            </Link>
          )}
        </p>
      ) : saved ? (
        <p className="flex items-center gap-2 text-body font-semibold text-accent-on-soft" role="status">
          <Check className="size-4" aria-hidden="true" />
          {t('vocabulary.card.saved')}
          <Link to="/vocabulary/$entryId" params={{ entryId: saved.id }} className="ml-auto text-link hover:underline">
            {t('vocabulary.card.open')}
          </Link>
        </p>
      ) : (
        <Button
          onClick={() => save.mutate({ term: lemma, context: picked.context, itemSetId, sourceLabel })}
          disabled={save.isPending}
        >
          <BookmarkPlus className="size-4" aria-hidden="true" />
          {t('vocabulary.card.save')}
        </Button>
      )}
      {save.isError && (
        <p role="alert" className="text-caption text-danger">
          {t('vocabulary.card.saveFailed')}
        </p>
      )}
    </div>
  );
}
