import { ArrowLeft, ArrowRight, Eye, EyeOff, Headphones, Pause, Play, Timer } from 'lucide-react';
import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Attempt, AttemptItem, AttemptReview } from '@/api/generated/practice';
import { Button } from '@/components/ui/button';
import { OfflineError } from '@/lib/practice/answer-sync';
import { isAnswered, type Answer } from '@/lib/practice/answers';
import { slotsFrom } from '@/lib/practice/chunks';
import { layoutOf } from '@/lib/practice/layout';
import { timerText } from '@/lib/timer';
import { cn } from '@/lib/cn';
import { AudioPlayer } from './audio-player';
import { ClozeParagraph } from './cloze-paragraph';
import { slotCount, type ItemContent, type Stimulus } from './content';
import { ExerciseItem } from './exercise-item';
import { Mcq } from './mcq';
import { ReportButton } from './report-dialog';
import { SentenceBuilder } from './sentence-builder';
import { DailyLifeText, Passage } from './stimulus-views';
import { useAttemptSession, type AttemptSession } from './use-attempt-session';

const SET_AUDIO = new Set(['LISTEN_CONVERSATION', 'LISTEN_ANNOUNCEMENT', 'LISTEN_ACADEMIC_TALK', 'LEGACY_LISTENING']);
const NOTES = new Set(['LISTEN_ACADEMIC_TALK', 'LEGACY_LISTENING']);
const PASSAGE = new Set(['READ_ACADEMIC_PASSAGE', 'LEGACY_READING']);

interface TaskPlayerProps {
  attempt: Attempt;
  onSubmitted: (review: AttemptReview) => void;
  onExit: () => void;
}

/** Task player of docs/07-ux-ui.md §5.9 for every closed task type in Practice mode (US-06.01). */
export function TaskPlayer({ attempt, onSubmitted, onExit }: TaskPlayerProps) {
  const { t } = useTranslation();
  const session = useAttemptSession(attempt);
  const items = [...attempt.items].sort((a, b) => a.ordinal - b.ordinal);
  const layout = layoutOf(attempt.taskType);
  const [index, setIndex] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [offline, setOffline] = useState(false);
  const [error, setError] = useState(false);
  const [timerHidden, setTimerHidden] = useState(false);
  const stimulus = (attempt.stimulus ?? {}) as Stimulus;
  const current = items[index];
  const legacy = attempt.taskType.startsWith('LEGACY_');

  useEffect(() => {
    if (current) session.setCurrentItem(current.itemId);
  }, [current, session]);

  const unanswered = items.filter((item) => !isAnswered(session.answers[item.itemId])).length;

  const submit = async () => {
    setConfirming(false);
    setSubmitting(true);
    setError(false);
    try {
      onSubmitted(await session.submit());
    } catch (cause) {
      if (cause instanceof OfflineError) {
        setOffline(true);
      } else {
        setError(true);
      }
    } finally {
      setSubmitting(false);
    }
  };

  // AC-06.03.2: the submission goes out by itself once the network is back
  const retrySubmit = useEffectEvent(() => {
    setOffline(false);
    void submit();
  });
  useEffect(() => {
    if (!offline) return;
    const retry = () => retrySubmit();
    window.addEventListener('online', retry);
    return () => window.removeEventListener('online', retry);
  }, [offline]);

  const requestSubmit = () => (unanswered > 0 ? setConfirming(true) : void submit());

  const exit = async () => {
    try {
      await session.flush();
    } catch {
      // the answers stay on this device and go out next time
    }
    onExit();
  };

  const norm = Math.round(attempt.estimatedSeconds * (attempt.timingMultiplier || 1));
  const clock = timerText(norm, attempt.activeSeconds + session.counted);
  const isLast = index === items.length - 1 || layout !== 'step';

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-e1">
      <header className="flex flex-wrap items-center gap-3 border-b border-border bg-surface px-4 py-3">
        <button
          type="button"
          onClick={() => void exit()}
          className="inline-flex items-center gap-1 text-caption font-semibold text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {t('practice.player.back')}
        </button>
        <h1 className="min-w-0 flex-1 truncate text-center text-body font-semibold text-ink">
          {t(`practice.taskType.${attempt.taskType}`)}
        </h1>
        <div className="flex items-center gap-1">
          {!timerHidden && (
            <span
              role="timer"
              aria-live="off"
              aria-label={clock.overtime ? t('practice.player.overtime') : t('practice.player.timer')}
              className={cn(
                'inline-flex items-center gap-1 rounded-lg bg-surface-sunken px-2 py-1 font-mono text-body tabular-nums',
                clock.overtime ? 'text-danger' : 'text-ink',
              )}
            >
              <Timer className="size-4" aria-hidden="true" />
              {clock.text}
            </span>
          )}
          <IconButton
            label={timerHidden ? t('practice.player.showTimer') : t('practice.player.hideTimer')}
            onClick={() => setTimerHidden(!timerHidden)}
          >
            {timerHidden ? <Eye className="size-4" /> : <EyeOff className="size-4" />}
          </IconButton>
          <IconButton
            label={session.paused ? t('practice.player.resume') : t('practice.player.pause')}
            onClick={session.paused ? session.resume : session.pause}
          >
            {session.paused ? <Play className="size-4" /> : <Pause className="size-4" />}
          </IconButton>
        </div>
        {layout === 'step' && (
          <p className="w-full text-right text-caption text-ink-muted">
            {t('practice.player.question', { current: index + 1, total: items.length })}
          </p>
        )}
      </header>
      {legacy && (
        <p className="border-b border-border bg-info-soft px-4 py-2 text-caption text-ink">
          {t('practice.player.legacyBanner')}
        </p>
      )}

      <div className="relative min-h-[420px] bg-surface-task">
        <div
          className={cn('p-4 md:p-6', (session.paused || session.idle) && 'pointer-events-none select-none blur-md')}
          aria-hidden={session.paused || session.idle || undefined}
          inert={session.paused || session.idle || undefined}
        >
          {layout === 'cloze' && <ClozeLayout stimulus={stimulus} items={items} session={session} />}
          {layout === 'list' && <ListLayout stimulus={stimulus} items={items} session={session} />}
          {layout === 'step' && current && (
            <StepLayout
              key={SET_AUDIO.has(attempt.taskType) ? 'set' : current.itemId}
              taskType={attempt.taskType}
              stimulus={stimulus}
              item={current}
              items={items}
              attemptId={attempt.attemptId}
              session={session}
            />
          )}
        </div>
        {(session.paused || session.idle) && (
          <div className="absolute inset-0 grid place-items-center p-6">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="pause-title"
              className="flex max-w-sm flex-col items-center gap-3 rounded-xl border border-border bg-surface p-6 text-center shadow-modal"
            >
              <h2 id="pause-title" className="text-title-3 font-semibold text-ink">
                {session.paused ? t('practice.player.pausedTitle') : t('practice.player.idleTitle')}
              </h2>
              <p className="text-body text-ink-muted">
                {session.paused ? t('practice.player.pausedText') : t('practice.player.idleText')}
              </p>
              <Button autoFocus onClick={session.paused ? session.resume : session.wake}>
                <Play className="size-4" aria-hidden="true" />
                {t('practice.player.resume')}
              </Button>
            </div>
          </div>
        )}
      </div>

      <footer className="flex flex-wrap items-center gap-3 border-t border-border bg-surface px-4 py-3">
        <ReportButton itemSetId={attempt.itemSetId} itemOrdinal={layout === 'step' ? current?.ordinal : undefined} />
        {layout === 'step' && items.length > 1 && (
          <nav aria-label={t('practice.player.dots')} className="flex flex-1 flex-wrap justify-center gap-1.5">
            {items.map((item, i) => {
              const done = isAnswered(session.answers[item.itemId]);
              return (
                <button
                  key={item.itemId}
                  type="button"
                  onClick={() => setIndex(i)}
                  aria-current={i === index ? 'step' : undefined}
                  aria-label={`${t('practice.player.goTo', { n: i + 1 })}${done ? `, ${t('practice.player.answered')}` : ''}`}
                  className={cn(
                    'size-3 rounded-full border-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring',
                    done ? 'border-primary bg-primary' : 'border-border-strong bg-transparent',
                    i === index && 'ring-2 ring-primary ring-offset-2 ring-offset-surface',
                  )}
                />
              );
            })}
          </nav>
        )}
        <div className="ml-auto flex items-center gap-2">
          <span aria-live="polite" className="text-caption text-ink-muted">
            {session.saveState === 'saving' && t('practice.player.saving')}
            {session.saveState === 'saved' && t('practice.player.saved')}
            {session.saveState === 'offline' && t('practice.player.savedOffline')}
          </span>
          {layout === 'step' && index > 0 && (
            <Button variant="ghost" onClick={() => setIndex(index - 1)}>
              <ArrowLeft className="size-4" aria-hidden="true" />
              {t('practice.player.previous')}
            </Button>
          )}
          {isLast ? (
            <Button onClick={requestSubmit} disabled={submitting || offline || session.paused}>
              {submitting ? t('practice.player.submitting') : t('practice.player.submit')}
            </Button>
          ) : (
            <Button onClick={() => setIndex(index + 1)}>
              {t('practice.player.next')}
              <ArrowRight className="size-4" aria-hidden="true" />
            </Button>
          )}
        </div>
        {(offline || error) && (
          <p role="alert" className="w-full text-right text-caption text-danger">
            {offline ? t('practice.player.offline') : t('practice.player.error')}
          </p>
        )}
      </footer>

      <SubmitConfirm
        open={confirming}
        unanswered={unanswered}
        onCancel={() => setConfirming(false)}
        onConfirm={() => void submit()}
      />
    </div>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="grid size-9 place-items-center rounded-lg text-ink-muted hover:bg-surface-sunken hover:text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
    >
      {children}
    </button>
  );
}

function SubmitConfirm({
  open,
  unanswered,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  unanswered: number;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t } = useTranslation();
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      onClose={onCancel}
      aria-labelledby="submit-title"
      aria-describedby="submit-text"
      className="m-auto w-[min(420px,94vw)] rounded-xl border border-border bg-surface p-6 text-ink shadow-modal backdrop:bg-black/40"
    >
      <h2 id="submit-title" className="text-title-3 font-semibold">
        {t('practice.player.unansweredTitle')}
      </h2>
      <p id="submit-text" className="mt-2 text-body">
        {t('practice.player.unanswered', { count: unanswered })}
      </p>
      <p className="mt-1 text-caption text-ink-muted">{t('practice.player.unansweredHint')}</p>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>
          {t('practice.player.cancel')}
        </Button>
        <Button onClick={onConfirm}>{t('practice.player.submitAnyway')}</Button>
      </div>
    </dialog>
  );
}

interface LayoutProps {
  stimulus: Stimulus;
  items: AttemptItem[];
  session: AttemptSession;
}

function contentOf(item: AttemptItem): ItemContent {
  return item.content as ItemContent;
}

function ClozeLayout({ stimulus, items, session }: LayoutProps) {
  const { t } = useTranslation();
  const lengths = items.map((item) => contentOf(item).missingLetters ?? 1);
  const values = items.map((item) => {
    const answer = session.answers[item.itemId];
    return answer?.type === 'TEXT' ? answer.text : '';
  });
  return (
    <div className="flex flex-col gap-4">
      <p className="text-center text-body font-semibold text-ink-muted">{t('practice.cloze.instructions')}</p>
      {stimulus.title && <h2 className="text-center text-title-3 font-semibold text-ink">{stimulus.title}</h2>}
      <ClozeParagraph
        markup={stimulus.markup ?? ''}
        lengths={lengths}
        values={values}
        onChange={(gap, text) => session.setAnswer(items[gap]!.itemId, text.trim() ? { type: 'TEXT', text } : null)}
      />
    </div>
  );
}

function ListLayout({ stimulus, items, session }: LayoutProps) {
  const unit = stimulus.unitRef;
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-col gap-1">
        {stimulus.instructions && <p className="text-body-lg font-semibold text-ink">{stimulus.instructions}</p>}
        {unit && (unit.topicTitle || unit.theme) && (
          <p className="text-caption text-ink-muted">{[unit.topicTitle, unit.theme].filter(Boolean).join(' · ')}</p>
        )}
      </div>
      <ol className="flex flex-col gap-6">
        {items.map((item) => (
          <li key={item.itemId} className="rounded-xl border border-border bg-surface p-4">
            <ExerciseItem
              item={contentOf(item)}
              ordinal={item.ordinal}
              answer={session.answers[item.itemId] ?? null}
              onAnswer={(answer) => session.setAnswer(item.itemId, answer)}
            />
          </li>
        ))}
      </ol>
    </div>
  );
}

interface StepLayoutProps {
  taskType: string;
  stimulus: Stimulus;
  item: AttemptItem;
  items: AttemptItem[];
  attemptId: string;
  session: AttemptSession;
}

function StepLayout({ taskType, stimulus, item, items, attemptId, session }: StepLayoutProps) {
  const { t } = useTranslation();
  const content = contentOf(item);
  const answer = session.answers[item.itemId] ?? null;
  const anyAnswered = items.some((i) => isAnswered(session.answers[i.itemId]));
  const [heard, setHeard] = useState(anyAnswered);
  const [heardItems, setHeardItems] = useState(
    () => new Set(items.filter((i) => isAnswered(session.answers[i.itemId])).map((i) => i.itemId)),
  );
  const [panel, setPanel] = useState<'passage' | 'question'>('passage');

  if (taskType === 'BUILD_A_SENTENCE') {
    const count = slotCount(content);
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        <p className="text-body font-semibold text-ink-muted">{t('practice.sentence.instructions')}</p>
        <SentenceBuilder
          item={content}
          value={slotsFrom(answer?.type === 'SEQUENCE' ? answer.fragmentIds : undefined, count)}
          onChange={(slots) => session.setAnswer(item.itemId, { type: 'SEQUENCE', fragmentIds: slots })}
        />
      </div>
    );
  }

  const question = <Question content={content} answer={answer} onAnswer={(a) => session.setAnswer(item.itemId, a)} />;

  if (taskType === 'LISTEN_CHOOSE_RESPONSE') {
    const itemHeard = heardItems.has(item.itemId);
    return (
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-5">
        <Headphones className="size-16 text-brand-accent" aria-hidden="true" />
        <p className="text-body-lg font-semibold text-ink">{t('practice.listening.listen')}</p>
        <div className="w-full">
          <AudioPlayer
            src={content.audio?.url}
            autoPlay={!itemHeard}
            onFinished={() => setHeardItems((set) => new Set(set).add(item.itemId))}
            onReplay={session.countReplay}
            onPlayingChange={session.setMediaActive}
          />
        </div>
        {itemHeard ? (
          <div className="w-full">
            <Mcq
              options={content.options ?? []}
              value={answer?.type === 'CHOICE' ? answer.optionIds : []}
              onChange={(ids) => session.setAnswer(item.itemId, { type: 'CHOICE', optionIds: ids })}
              label={t('practice.listening.choose')}
            />
          </div>
        ) : (
          <p className="text-caption text-ink-muted">{t('practice.listening.questionsAfter')}</p>
        )}
      </div>
    );
  }

  if (SET_AUDIO.has(taskType)) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-5">
        <div className="flex flex-col items-center gap-3 text-center">
          <Headphones className="size-12 text-brand-accent" aria-hidden="true" />
          {stimulus.contextLine && <p className="text-title-3 font-semibold text-ink">{stimulus.contextLine}</p>}
        </div>
        <AudioPlayer
          src={stimulus.audio?.url}
          autoPlay={!heard}
          onFinished={() => setHeard(true)}
          onReplay={session.countReplay}
          onPlayingChange={session.setMediaActive}
        />
        {heard ? (
          <div key={item.itemId}>{question}</div>
        ) : (
          <p className="text-center text-caption text-ink-muted">{t('practice.listening.questionsAfter')}</p>
        )}
        {NOTES.has(taskType) && <Notes attemptId={attemptId} />}
      </div>
    );
  }

  if (PASSAGE.has(taskType)) {
    const highlight = content.targetWord ?? content.anchor ?? null;
    const insertion = content.insertion
      ? {
          sentence: content.insertion.sentence,
          chosen: answer?.type === 'CHOICE' ? (answer.optionIds[0] ?? null) : null,
          onChoose: (key: string) => session.setAnswer(item.itemId, { type: 'CHOICE', optionIds: [key] }),
        }
      : null;
    return (
      <div className="flex flex-col gap-4">
        <div
          role="tablist"
          aria-label={t('practice.passage.tabs')}
          className="flex gap-1 self-center rounded-lg bg-surface-sunken p-1 lg:hidden"
        >
          {(['passage', 'question'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={panel === tab}
              onClick={() => setPanel(tab)}
              className={cn(
                'rounded-md px-4 py-1.5 text-caption font-semibold',
                panel === tab ? 'bg-surface text-ink shadow-e1' : 'text-ink-muted',
              )}
            >
              {t(`practice.passage.${tab}`)}
            </button>
          ))}
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <div
            tabIndex={0}
            aria-label={t('practice.passage.passage')}
            className={cn(
              'max-h-[60vh] overflow-auto rounded-xl border border-border bg-surface p-5 focus-visible:outline-2 focus-visible:outline-focus-ring lg:block',
              panel === 'passage' ? 'block' : 'hidden',
            )}
          >
            <Passage
              stimulus={stimulus}
              highlight={highlight}
              markers={content.insertion?.markers}
              insertion={insertion}
            />
          </div>
          <div className={cn('flex flex-col gap-4 lg:flex', panel === 'question' ? 'flex' : 'hidden')}>
            {content.insertion && (
              <div className="rounded-xl border border-border bg-surface p-4">
                <p className="text-caption font-semibold text-ink-muted">{t('practice.passage.insert')}</p>
                <p className="mt-1 text-body font-medium text-ink">{content.insertion.sentence}</p>
              </div>
            )}
            {question}
          </div>
        </div>
      </div>
    );
  }

  // READ_DAILY_LIFE
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <details open className="group lg:pointer-events-none" aria-label={stimulus.title}>
        <summary className="mb-2 cursor-pointer list-none text-caption font-semibold text-ink-muted lg:hidden">
          <span className="group-open:hidden">{t('practice.passage.showText')}</span>
          <span className="hidden group-open:inline">{t('practice.passage.hideText')}</span>
        </summary>
        <div className="lg:pointer-events-auto">
          <DailyLifeText stimulus={stimulus} />
        </div>
      </details>
      <div className="flex flex-col gap-4">{question}</div>
    </div>
  );
}

function Question({
  content,
  answer,
  onAnswer,
}: {
  content: ItemContent;
  answer: Answer | null;
  onAnswer: (answer: Answer) => void;
}) {
  const { t } = useTranslation();
  const multiple = content.selection === 'MULTI' ? (content.subskill === 'PROSE_SUMMARY' ? 3 : 2) : undefined;
  return (
    <div className="flex flex-col gap-4">
      {content.introSentence && (
        <p className="rounded-lg bg-surface p-3 text-body font-medium text-ink">{content.introSentence}</p>
      )}
      <h2 className="text-body-lg font-semibold text-ink">{content.prompt}</h2>
      <Mcq
        options={content.options ?? []}
        value={answer?.type === 'CHOICE' ? answer.optionIds : []}
        onChange={(ids) => onAnswer({ type: 'CHOICE', optionIds: ids })}
        label={content.prompt ?? t('practice.mcq.options')}
        multiple={multiple}
      />
    </div>
  );
}

function Notes({ attemptId }: { attemptId: string }) {
  const { t } = useTranslation();
  const key = `toefly.notes.${attemptId}`;
  const [text, setText] = useState(() => {
    try {
      return sessionStorage.getItem(key) ?? '';
    } catch {
      return '';
    }
  });
  return (
    <label className="flex flex-col gap-1 text-caption font-semibold text-ink-muted">
      {t('practice.listening.notes')}
      <textarea
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          try {
            sessionStorage.setItem(key, event.target.value);
          } catch {
            // notes are a convenience
          }
        }}
        rows={4}
        spellCheck={false}
        className="rounded-lg border border-border bg-surface p-3 text-body font-normal text-ink"
      />
      <span className="font-normal">{t('practice.listening.notesHint')}</span>
    </label>
  );
}
