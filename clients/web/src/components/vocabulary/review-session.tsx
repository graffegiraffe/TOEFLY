import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Rating, ReviewSessionStatusEnum, type ReviewCard, type ReviewSession } from '@/api/generated/vocabulary';
import { Button } from '@/components/ui/button';
import { ProgressBar } from '@/components/ui/progress-bar';
import { cn } from '@/lib/cn';
import { ReviewQueue, clozeOf, formatInterval, nextInLabel } from '@/lib/vocabulary';

const RATINGS: { rating: Rating; key: string; interval: keyof ReviewCard['intervals'] }[] = [
  { rating: Rating.Again, key: '1', interval: 'again' },
  { rating: Rating.Hard, key: '2', interval: 'hard' },
  { rating: Rating.Good, key: '3', interval: 'good' },
  { rating: Rating.Easy, key: '4', interval: 'easy' },
];

export interface ReviewSessionViewProps {
  session: ReviewSession;
  grade: (card: ReviewCard, rating: Rating, sequenceNo: number, elapsedMs: number) => Promise<ReviewCard>;
  complete: () => Promise<ReviewSession>;
  onDone: () => void;
  now?: () => number;
}

/**
 * FSRS review session (docs/07-ux-ui.md §5.14, US-15.03): one card at a time, "Show answer" (Space), four grades with
 * the interval under each (keys 1–4), "7 of 12", and the summary "12 reviewed · next in 6 h". A card graded Again
 * comes back in the same session (AC-15.03.5).
 */
export function ReviewSessionView({ session, grade, complete, onDone, now = Date.now }: ReviewSessionViewProps) {
  const { t } = useTranslation();
  const queue = useMemo(() => new ReviewQueue(session.cards), [session]);
  const [cards, setCards] = useState(() => new Map(session.cards.map((card) => [card.id, card])));
  const [revealed, setRevealed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const [summary, setSummary] = useState<ReviewSession | null>(null);
  const shownAt = useRef(now());
  const [, setTick] = useState(0);

  const currentId = queue.current(now())?.id;
  const card = currentId ? cards.get(currentId) : undefined;
  const { done, total } = queue.progress();

  const finish = useCallback(async () => {
    try {
      setSummary(await complete());
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }, [complete]);

  const answer = useCallback(
    async (rating: Rating) => {
      if (!card || busy) return;
      setBusy(true);
      setFailed(false);
      try {
        const updated = await grade(card, rating, queue.sequence(), Math.max(0, now() - shownAt.current));
        const learning = updated.card.state === 'LEARNING' || updated.card.state === 'RELEARNING';
        const dueIn = Math.max(0, (updated.card.due.getTime() - now()) / 1000);
        queue.answer(card, { learning, dueInSeconds: dueIn }, now());
        setCards((previous) => new Map(previous).set(updated.id, updated));
        setRevealed(false);
        shownAt.current = now();
        setTick((tick) => tick + 1);
      } catch {
        setFailed(true);
        setBusy(false);
        return;
      }
      if (queue.finished()) {
        await finish();
      } else {
        setBusy(false);
      }
    },
    [card, busy, grade, queue, now, finish],
  );

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLElement && ['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target.tagName)) {
        return;
      }
      if (event.key === ' ' && !revealed && card) {
        event.preventDefault();
        setRevealed(true);
        return;
      }
      const choice = RATINGS.find((entry) => entry.key === event.key);
      if (choice && revealed) {
        event.preventDefault();
        void answer(choice.rating);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [answer, revealed, card]);

  const completed = summary ?? (session.status === ReviewSessionStatusEnum.Completed ? session : null);
  if (completed) {
    const result = completed;
    return (
      <section className="mx-auto flex w-full max-w-[520px] flex-col items-center gap-4 rounded-[20px] border border-border bg-surface-task p-8 text-center shadow-e1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">
          {result.planned === 0 ? t('vocabulary.review.nothingTitle') : t('vocabulary.review.doneTitle')}
        </h1>
        <p className="text-body text-ink" data-testid="review-summary">
          {result.nextDueInSeconds != null
            ? t('vocabulary.review.summaryNext', {
                count: result.reviewed,
                next: nextInLabel(result.nextDueInSeconds),
              })
            : t('vocabulary.review.summary', { count: result.reviewed })}
        </p>
        <Button onClick={onDone}>{t('vocabulary.review.back')}</Button>
      </section>
    );
  }

  if (!card) {
    return (
      <div
        className="mx-auto h-80 w-full max-w-[520px] animate-pulse rounded-[20px] bg-surface-sunken"
        aria-busy="true"
      >
        {failed && <p className="p-6 text-body text-danger">{t('vocabulary.review.failed')}</p>}
      </div>
    );
  }

  return (
    <section
      className="mx-auto flex w-full max-w-[520px] flex-col gap-5"
      aria-labelledby="review-heading"
      data-testid="review-session"
    >
      <div className="flex items-center justify-between gap-4">
        <h1 id="review-heading" className="text-title-3 font-semibold text-ink">
          {t('vocabulary.review.title')}
        </h1>
        <span className="text-caption font-semibold text-ink-muted" data-testid="review-progress">
          {t('vocabulary.review.progress', { done: Math.min(done + 1, total), total })}
        </span>
      </div>
      <ProgressBar value={total === 0 ? 0 : (100 * done) / total} label={t('vocabulary.review.title')} />
      <article className="flex min-h-72 flex-col gap-4 rounded-[20px] border border-border bg-surface-task p-8 shadow-e1">
        <Front card={card} />
        <div aria-live="polite">{revealed && <Back card={card} />}</div>
      </article>
      {failed && (
        <p role="alert" className="text-body text-danger">
          {t('vocabulary.review.failed')}
        </p>
      )}
      {!revealed ? (
        <Button block onClick={() => setRevealed(true)} aria-keyshortcuts="Space">
          {t('vocabulary.review.show')}
        </Button>
      ) : (
        <div
          className="grid grid-cols-2 gap-2 sm:grid-cols-4"
          role="group"
          aria-label={t('vocabulary.review.gradeGroup')}
        >
          {RATINGS.map(({ rating, key, interval }) => (
            <button
              key={rating}
              type="button"
              disabled={busy}
              onClick={() => void answer(rating)}
              aria-keyshortcuts={key}
              className={cn(
                'flex flex-col items-center gap-0.5 rounded-lg border border-border px-3 py-2 text-body font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring disabled:opacity-50',
                rating === 'AGAIN' && 'text-danger hover:bg-danger-soft',
                rating === 'HARD' && 'text-warning hover:bg-warning-soft',
                rating === 'GOOD' && 'text-accent-on-soft hover:bg-accent-soft',
                rating === 'EASY' && 'text-link hover:bg-surface-sunken',
              )}
            >
              <span>{t(`vocabulary.rating.${rating}`)}</span>
              <span className="text-caption font-normal text-ink-muted">
                {formatInterval(card.intervals[interval])}
              </span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

function Front({ card }: { card: ReviewCard }) {
  const { t } = useTranslation();
  if (card.format === 'DEFINITION_TO_WORD' && card.data.definitionEn) {
    return (
      <div className="flex flex-col gap-2">
        <p className="text-caption font-semibold uppercase tracking-wide text-ink-muted">
          {t('vocabulary.review.whichWord')}
        </p>
        <p className="text-title-3 text-ink" data-testid="card-front">
          {card.data.definitionEn}
        </p>
      </div>
    );
  }
  const cloze = card.format === 'CLOZE' ? clozeOf(card.contextSentence, card.lemma) : null;
  if (cloze) {
    return (
      <div className="flex flex-col gap-2">
        <p className="text-caption font-semibold uppercase tracking-wide text-ink-muted">
          {t('vocabulary.review.fillGap')}
        </p>
        <p className="text-title-3 text-ink" data-testid="card-front">
          {cloze.before}
          <span
            className="inline-block min-w-16 border-b-2 border-tag-vocabulary-fg"
            aria-label={t('vocabulary.review.gap')}
          />
          {cloze.after}
        </p>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1">
      <p className="text-title-1 font-semibold tracking-tight text-ink" data-testid="card-front">
        {card.lemma}
      </p>
      {card.data.partOfSpeech && <p className="text-caption text-ink-muted">{card.data.partOfSpeech}</p>}
      {card.contextSentence && <p className="text-body text-ink-muted">{card.contextSentence}</p>}
    </div>
  );
}

function Back({ card }: { card: ReviewCard }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-2 border-t border-border pt-4" data-testid="card-back">
      {card.format !== 'WORD_TO_DEFINITION' && (
        <p className="text-title-2 font-semibold text-ink">
          {card.lemma}
          {card.data.ipa && <span className="ml-2 text-body font-normal text-ink-muted">{card.data.ipa}</span>}
        </p>
      )}
      {card.format === 'WORD_TO_DEFINITION' && card.data.ipa && (
        <p className="text-body text-ink-muted">{card.data.ipa}</p>
      )}
      {card.format !== 'DEFINITION_TO_WORD' && (
        <p className="text-body text-ink">{card.data.definitionEn ?? t('vocabulary.definitionPending')}</p>
      )}
      {card.data.translationRu && (
        <p className="text-body text-ink-muted" lang="ru">
          {card.data.translationRu}
        </p>
      )}
      {card.format !== 'CLOZE' && card.data.exampleEn && (
        <p className="text-body italic text-ink-muted">{card.data.exampleEn}</p>
      )}
      {card.format === 'CLOZE' && card.contextSentence && (
        <p className="text-body italic text-ink-muted">{card.contextSentence}</p>
      )}
    </div>
  );
}
