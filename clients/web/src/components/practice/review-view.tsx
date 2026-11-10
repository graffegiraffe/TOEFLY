import { CircleCheck, CircleX, MessageCircle, Sparkles } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Attempt, AttemptReview, ItemReview } from '@/api/generated/practice';
import { Button } from '@/components/ui/button';
import { answerText, fromApi, type Answer } from '@/lib/practice/answers';
import { parseCloze } from '@/lib/practice/cloze';
import { layoutOf } from '@/lib/practice/layout';
import { formatClock, paceDeviation } from '@/lib/timer';
import { cn } from '@/lib/cn';
import { templateOf, type ItemContent, type Stimulus } from './content';

interface ReviewViewProps {
  review: AttemptReview;
  attempt: Attempt;
  onTryAgain: () => void;
  onBack: () => void;
  tryingAgain?: boolean;
}

const SECTION_NAMES: Record<string, string> = {
  READING: 'Reading',
  LISTENING: 'Listening',
  WRITING: 'Writing',
  SPEAKING: 'Speaking',
};

/** Review of a closed task (docs/07-ux-ui.md §5.10, US-11.01). */
export function ReviewView({ review, attempt, onTryAgain, onBack, tryingAgain }: ReviewViewProps) {
  const { t } = useTranslation();
  const stimulus = (attempt.stimulus ?? {}) as Stimulus;
  const contents = new Map(attempt.items.map((item) => [item.ordinal, item.content as ItemContent]));
  const items = [...review.items].sort((a, b) => a.ordinal - b.ordinal);
  const listening = review.section === 'LISTENING';
  const pace = paceDeviation(review.activeSeconds, review.estimatedSeconds);
  const change = review.abilityChange;
  const delta = change ? change.thetaAfter - change.thetaBefore : 0;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center gap-6 rounded-xl border border-border bg-surface p-6 shadow-e1">
        <ScoreRing correct={review.score.correct} total={review.score.total} label={t('review.scoreLabel')} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <p className="text-caption font-semibold uppercase tracking-wide text-ink-muted">
            {t(`practice.taskType.${review.taskType}`)}
          </p>
          <h1 className="text-title-1 font-semibold tracking-tight text-ink">
            {t('review.score', { correct: review.score.correct, total: review.score.total })}
          </h1>
          <p className="text-body text-ink">
            <span className="text-ink-muted">{t('review.time')}: </span>
            {t('review.timeValue', {
              active: formatClock(review.activeSeconds),
              norm: formatClock(review.estimatedSeconds),
            })}
          </p>
          {pace !== null && (
            <p className={cn('text-caption', pace > 0 ? 'text-warning' : 'text-ink-muted')}>
              {pace > 0 ? t('review.slower', { percent: pace }) : t('review.faster', { percent: Math.abs(pace) })}
            </p>
          )}
          {change && Math.abs(delta) >= 0.005 && (
            <p className="text-caption font-semibold text-accent-on-soft">
              {t('review.ability', {
                delta: `${delta > 0 ? '+' : '−'}${Math.abs(delta).toFixed(2)}`,
                section: SECTION_NAMES[change.section] ?? change.section,
              })}
            </p>
          )}
          {listening && (review.audioReplays ?? 0) > 0 && (
            <p className="text-caption text-ink-muted">
              {t('practice.listening.replays', { count: review.audioReplays ?? 0 })}
            </p>
          )}
        </div>
      </header>

      {review.explanationPending && <p className="text-caption text-ink-muted">{t('review.pending')}</p>}

      {listening && <Transcript stimulus={stimulus} items={attempt.items.map((i) => i.content as ItemContent)} />}

      {layoutOf(review.taskType) === 'cloze' && <ClozeReview stimulus={stimulus} items={items} contents={contents} />}

      <ol className="flex flex-col gap-3">
        {items.map((item) => (
          <ItemCard
            key={item.itemId}
            item={item}
            content={contents.get(item.ordinal) ?? {}}
            taskType={review.taskType}
          />
        ))}
      </ol>

      <div className="flex flex-wrap gap-3">
        <Button onClick={onTryAgain} disabled={tryingAgain}>
          {t('review.tryAgain')}
        </Button>
        <Button variant="secondary" onClick={onBack}>
          {t('review.backToPractice')}
        </Button>
      </div>
    </div>
  );
}

function ScoreRing({ correct, total, label }: { correct: number; total: number; label: string }) {
  const ratio = total > 0 ? correct / total : 0;
  const circumference = 2 * Math.PI * 30;
  return (
    <svg width="76" height="76" viewBox="0 0 76 76" role="img" aria-label={`${label}: ${correct}/${total}`}>
      <circle cx="38" cy="38" r="30" fill="none" strokeWidth="8" className="stroke-surface-sunken" />
      <circle
        cx="38"
        cy="38"
        r="30"
        fill="none"
        strokeWidth="8"
        strokeLinecap="round"
        className="stroke-primary"
        strokeDasharray={`${circumference * ratio} ${circumference}`}
        transform="rotate(-90 38 38)"
      />
      <text x="38" y="43" textAnchor="middle" className="fill-ink text-[15px] font-semibold">
        {Math.round(ratio * 100)}%
      </text>
    </svg>
  );
}

function Transcript({ stimulus, items }: { stimulus: Stimulus; items: ItemContent[] }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const speakers = new Map((stimulus.speakers ?? []).map((s) => [s.id, s.name ?? s.role]));
  const lines: { speaker?: string; text: string }[] = Array.isArray(stimulus.transcript)
    ? stimulus.transcript.map((line) => ({ speaker: speakers.get(line.speaker) ?? line.speaker, text: line.text }))
    : items
        .map((item) => (item as { transcript?: string }).transcript)
        .filter((text): text is string => Boolean(text))
        .map((text) => ({ text }));
  if (lines.length === 0) return null;
  return (
    <section className="rounded-xl border border-border bg-surface p-4">
      <Button variant="ghost" onClick={() => setOpen(!open)} aria-expanded={open}>
        {open ? t('review.hideTranscript') : t('review.showTranscript')}
      </Button>
      {open && (
        <div className="mt-3 flex flex-col gap-2 text-body text-ink">
          <h2 className="sr-only">{t('review.transcript')}</h2>
          {lines.map((line, i) => (
            <p key={i}>
              {line.speaker && <span className="font-semibold">{line.speaker}: </span>}
              {line.text}
            </p>
          ))}
        </div>
      )}
    </section>
  );
}

function ClozeReview({
  stimulus,
  items,
  contents,
}: {
  stimulus: Stimulus;
  items: ItemReview[];
  contents: Map<number, ItemContent>;
}) {
  const segments = parseCloze(stimulus.markup ?? '');
  return (
    <p className="mx-auto max-w-[68ch] rounded-xl border border-border bg-surface-task p-5 text-body-lg leading-[2.6] text-ink">
      {segments.map((segment, i) => {
        if (segment.kind === 'text') return <span key={i}>{segment.text}</span>;
        const item = items[segment.index];
        const wrote = textOf(fromApi(item?.userAnswer));
        const key = textOf(fromApi(item?.correctAnswer));
        const prefix = contents.get(item?.ordinal ?? -1)?.prefix ?? segment.prefix;
        return (
          <span key={i} className="relative inline-block">
            {!item?.correct && (
              <span className="absolute -top-4 left-0 whitespace-nowrap text-micro text-success">
                {prefix}
                {key}
              </span>
            )}
            <span
              className={cn('border-b-2', item?.correct ? 'border-success text-success' : 'border-danger text-danger')}
            >
              {prefix}
              {wrote || '_'.repeat(Math.max(1, key.length))}
            </span>
          </span>
        );
      })}
    </p>
  );
}

function textOf(answer: Answer | null): string {
  return answer?.type === 'TEXT' ? answer.text : '';
}

function ItemCard({ item, content, taskType }: { item: ItemReview; content: ItemContent; taskType: string }) {
  const { t } = useTranslation();
  const user = fromApi(item.userAnswer);
  const correct = fromApi(item.correctAnswer);
  const status = !user ? 'unanswered' : item.correct ? 'correct' : 'wrong';
  const title = content.prompt ?? content.word ?? content.sentence ?? content.source ?? content.contextLine;

  return (
    <li>
      <details open={!item.correct} className="group rounded-xl border border-border bg-surface">
        <summary className="flex cursor-pointer list-none items-center gap-3 p-4 focus-visible:outline-2 focus-visible:outline-focus-ring">
          {item.correct ? (
            <CircleCheck className="size-5 shrink-0 text-success" aria-hidden="true" />
          ) : (
            <CircleX className="size-5 shrink-0 text-danger" aria-hidden="true" />
          )}
          <span className="min-w-0 flex-1">
            <span className="block text-caption text-ink-muted">
              {t('review.question', { n: item.ordinal })} · {t(`review.${status}`)}
            </span>
            {title && taskType !== 'COMPLETE_THE_WORDS' && (
              <span className="block text-body font-semibold text-ink">{title}</span>
            )}
            {taskType === 'COMPLETE_THE_WORDS' && (
              <span className="block text-body font-semibold text-ink">
                {content.prefix}
                {textOf(correct)}
              </span>
            )}
          </span>
        </summary>
        <div className="flex flex-col gap-3 border-t border-border p-4">
          <ItemBody item={item} content={content} user={user} correct={correct} taskType={taskType} />
          <Button
            variant="ghost"
            className="self-start"
            disabled
            title={t('review.askTutorSoon')}
            aria-describedby={`ask-${item.itemId}`}
          >
            <MessageCircle className="size-4" aria-hidden="true" />
            {t('review.askTutor')}
          </Button>
          <span id={`ask-${item.itemId}`} className="sr-only">
            {t('review.askTutorSoon')}
          </span>
        </div>
      </details>
    </li>
  );
}

function ItemBody({
  item,
  content,
  user,
  correct,
  taskType,
}: {
  item: ItemReview;
  content: ItemContent;
  user: Answer | null;
  correct: Answer | null;
  taskType: string;
}) {
  const { t } = useTranslation();
  const why = (key?: string) => (key ? t('review.whyCorrect', { key }) : t('review.whyCorrectPlain'));

  if (content.options && content.options.length > 0 && correct?.type === 'CHOICE') {
    const chosen = new Set(user?.type === 'CHOICE' ? user.optionIds : []);
    const right = new Set(correct.optionIds);
    const distractors = new Map((item.distractorExplanations ?? []).map((d) => [d.optionId, d]));
    return (
      <>
        <ul className="flex flex-col gap-2">
          {content.options.map((option) => {
            const isRight = right.has(option.key);
            const isChosen = chosen.has(option.key);
            const note = distractors.get(option.key);
            return (
              <li
                key={option.key}
                className={cn(
                  'flex flex-col gap-1 rounded-[12px] border px-4 py-3 text-body',
                  isRight
                    ? 'border-success bg-success-soft'
                    : isChosen
                      ? 'border-danger bg-danger-soft'
                      : 'border-border bg-surface-task',
                )}
              >
                <span className="flex items-start gap-2">
                  {isRight ? (
                    <CircleCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                  ) : isChosen ? (
                    <CircleX className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
                  ) : (
                    <span className="size-4 shrink-0" />
                  )}
                  <span className="w-5 shrink-0 font-semibold">{option.key}</span>
                  <span className="flex-1">{option.text}</span>
                  {isRight && (
                    <span className="text-caption font-semibold text-success">{t('review.correctAnswer')}</span>
                  )}
                  {isChosen && !isRight && (
                    <span className="text-caption font-semibold text-danger">{t('review.yourAnswer')}</span>
                  )}
                </span>
                {!isRight && note && (
                  <Explanation title={t('review.whyNot', { key: option.key })} ai={note.source === 'AI'}>
                    {note.text}
                  </Explanation>
                )}
              </li>
            );
          })}
        </ul>
        {item.explanation && (
          <Explanation title={why([...right].join(', '))} ai={item.explanation.source === 'AI'}>
            {item.explanation.text}
          </Explanation>
        )}
      </>
    );
  }

  if (taskType === 'BUILD_A_SENTENCE') {
    const chunkText = (id: string) => content.chunks?.find((chunk) => chunk.id === id)?.text ?? id;
    const sentence = (answer: Answer | null) => assemble(content, answer, chunkText);
    const correctIds = new Set(correct?.type === 'SEQUENCE' ? correct.fragmentIds : []);
    const extra = (user?.type === 'SEQUENCE' ? user.fragmentIds : []).filter(
      (id): id is string => id !== null && !correctIds.has(id),
    );
    return (
      <>
        <p className="text-body">
          <span className="text-ink-muted">{t('review.yourSentence')}: </span>
          <span className={item.correct ? 'text-success' : 'text-danger'}>{sentence(user)}</span>
        </p>
        <p className="text-body">
          <span className="text-ink-muted">{t('review.correctSentence')}: </span>
          <span className="font-semibold text-success">{sentence(correct)}</span>
        </p>
        {extra.map((id) => (
          <p key={id} className="text-caption text-danger">
            “{chunkText(id)}” — {t('review.extraWord')}
          </p>
        ))}
        {item.explanation && (
          <Explanation title={why()} ai={item.explanation.source === 'AI'}>
            {item.explanation.text}
          </Explanation>
        )}
      </>
    );
  }

  const prefix = taskType === 'COMPLETE_THE_WORDS' ? (content.prefix ?? '') : '';
  return (
    <>
      <p className="text-body">
        <span className="text-ink-muted">{t('review.yourAnswer')}: </span>
        <span className={item.correct ? 'text-success' : 'text-danger'}>
          {user ? `${prefix}${answerText(user)}` : t('review.unanswered')}
        </span>
      </p>
      {!item.correct && (
        <p className="text-body">
          <span className="text-ink-muted">{t('review.correctAnswer')}: </span>
          <span className="font-semibold text-success">
            {prefix}
            {answerText(correct)}
          </span>
        </p>
      )}
      {item.explanation && (
        <Explanation title={why()} ai={item.explanation.source === 'AI'}>
          {item.explanation.text}
        </Explanation>
      )}
    </>
  );
}

function assemble(content: ItemContent, answer: Answer | null, chunkText: (id: string) => string): string {
  const ids = answer?.type === 'SEQUENCE' ? answer.fragmentIds : [];
  let slot = 0;
  return templateOf(content)
    .map((part) => {
      if (part.kind === 'FIXED') return part.text ?? '';
      const id = ids[slot++];
      return id ? chunkText(id) : '___';
    })
    .join(' ');
}

function Explanation({ title, ai, children }: { title: string; ai?: boolean; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="rounded-lg bg-surface-sunken p-3 text-body text-ink">
      <p className="mb-1 flex items-center gap-2 text-caption font-semibold text-ink-muted">
        {title}
        {ai && (
          <span className="inline-flex items-center gap-1 text-accent-on-soft">
            <Sparkles className="size-3" aria-hidden="true" />
            {t('review.aiExplanation')}
          </span>
        )}
      </p>
      {children}
    </div>
  );
}
