import { ArrowLeft, Timer } from 'lucide-react';
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { saveDraft, useSubmitSubmission, DraftConflictError } from '@/api/assessment';
import { AttemptMode, type Submission, type SubmissionVersion } from '@/api/generated/assessment';
import type { ItemSet } from '@/api/generated/content';
import { newKey } from '@/api/practice';
import { useHideTutor, useTutorScreen } from '@/components/tutor/use-tutor';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/cn';
import { timerText } from '@/lib/timer';
import { countWords, defaultTimeLimit, DISCUSSION_MIN_WORDS, EMAIL_SHORT_WORDS } from '@/lib/writing';
import { CommentCard } from './comment-card';
import { ConfirmDialog } from './confirm-dialog';
import { stimulusOf } from './content';
import { EssayEditor } from './essay-editor';
import { TaskCard } from './task-card';
import { reviewComments } from './comments';

const SAVE_DELAY_MS = 1500;
const SAVE_EVERY_MS = 10_000;

type SaveState = 'idle' | 'saving' | 'saved' | 'error';

interface WritingEditorProps {
  submission: Submission;
  /** Aggregate version for If-Match. */
  version: number;
  draft: SubmissionVersion;
  itemSet: ItemSet | undefined;
  onSubmitted: (submission: Submission) => void;
  onExit: () => void;
}

/**
 * The Writing players (docs/07-ux-ui.md §5.9 items 9–10, §6.3): the task on the left, the EssayEditor on the right,
 * a 7:00/10:00 countdown for the draft and a comments panel instead of the timer for Attempt 2 and 3.
 */
export function WritingEditor({ submission, version, draft, itemSet, onSubmitted, onExit }: WritingEditorProps) {
  const { t } = useTranslation();
  const stimulus = stimulusOf(itemSet);
  const isEmail = submission.taskType === 'WRITE_EMAIL';
  const revision = draft.versionNo > 1;
  const exam = submission.mode === AttemptMode.Exam;
  // AC-17.05.1: no AI Tutor in an exam; for a draft the service refuses to write the response (AC-17.03.2).
  useHideTutor(exam);
  useTutorScreen(
    exam
      ? null
      : {
          type: 'SUBMISSION_DRAFT',
          ref: submission.id,
          label: t('tutor.context.essay', { version: t(`writing.version.${draft.versionNo}`) }),
        },
  );
  const limit = submission.timeLimitSeconds ?? stimulus.timeLimitSeconds ?? defaultTimeLimit(submission.taskType);

  const [text, setText] = useState(draft.text);
  const [elapsed, setElapsed] = useState(draft.activeSeconds);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [confirming, setConfirming] = useState(false);
  const [failed, setFailed] = useState(false);
  const submit = useSubmitSubmission(submission.id);
  const submitKey = useRef(newKey());

  const latest = useRef({ text: draft.text, elapsed: draft.activeSeconds });
  useEffect(() => {
    latest.current = { text, elapsed };
  }, [text, elapsed]);
  const saved = useRef(draft.text);
  const etag = useRef(version);
  const running = useRef<Promise<void> | null>(null);

  const flush = useCallback(async (): Promise<void> => {
    while (running.current) await running.current;
    const { text: current, elapsed: seconds } = latest.current;
    if (current === saved.current) return;
    setSaveState('saving');
    const job = saveDraft(submission.id, etag.current, current, seconds)
      .then((next) => {
        etag.current = next;
        saved.current = current;
        setSaveState(latest.current.text === current ? 'saved' : 'saving');
      })
      .catch((error: unknown) => {
        setSaveState('error');
        throw error;
      })
      .finally(() => {
        running.current = null;
      });
    running.current = job.catch(() => undefined);
    await job;
  }, [submission.id]);

  // Debounced autosave after typing, and every 10 s while something is unsaved (docs/07-ux-ui.md §3.12).
  useEffect(() => {
    if (text === saved.current) return;
    const timer = setTimeout(() => void flush().catch(() => undefined), SAVE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [text, flush]);
  useEffect(() => {
    const timer = setInterval(() => void flush().catch(() => undefined), SAVE_EVERY_MS);
    return () => clearInterval(timer);
  }, [flush]);

  const words = countWords(text);

  const send = async () => {
    setConfirming(false);
    setFailed(false);
    try {
      await flush();
      onSubmitted(await submit.mutateAsync(submitKey.current));
    } catch (error) {
      if (!(error instanceof DraftConflictError)) setFailed(true);
      else onSubmitted(submission);
    }
  };

  const requestSubmit = () => {
    if (!exam && isEmail && words < EMAIL_SHORT_WORDS) setConfirming(true);
    else void send();
  };

  // Active time: seconds while the tab is visible. In Exam the response goes out by itself at 0:00 (AC-09.02.2);
  // Exam sessions come with phase 7.
  const autoSent = useRef(false);
  const tick = useEffectEvent(() => {
    if (document.visibilityState !== 'visible') return;
    setElapsed(elapsed + 1);
    if (exam && !revision && elapsed + 1 >= limit && !autoSent.current) {
      autoSent.current = true;
      void send();
    }
  });
  useEffect(() => {
    const timer = setInterval(() => tick(), 1000);
    return () => clearInterval(timer);
  }, []);

  const exit = async () => {
    try {
      await flush();
    } catch {
      // the next visit loads the last saved text
    }
    onExit();
  };

  const clock = timerText(limit, elapsed);
  const previous = submission.versions.find((item) => item.versionNo === draft.versionNo - 1);
  const previousComments = previous ? reviewComments(previous) : [];
  const busy = submit.isPending;

  const editor = (
    <section aria-label={t('writing.editor.response')} className="flex min-w-0 flex-col gap-3">
      {isEmail && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-lg border border-border bg-surface-sunken px-3 py-2 text-body">
          <dt className="font-semibold text-ink-muted">{t('writing.email.to')}</dt>
          <dd className="text-ink" data-testid="email-to">
            {stimulus.recipient?.name}
          </dd>
          <dt className="font-semibold text-ink-muted">{t('writing.email.subject')}</dt>
          <dd className="text-ink" data-testid="email-subject">
            {stimulus.subject}
          </dd>
        </dl>
      )}
      <EssayEditor
        value={text}
        onChange={setText}
        onBlur={() => void flush().catch(() => undefined)}
        minWords={isEmail ? undefined : (stimulus.minWords ?? DISCUSSION_MIN_WORDS)}
        disabled={busy}
      />
    </section>
  );

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-e1">
      <header className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-3">
        <button
          type="button"
          onClick={() => void exit()}
          className="inline-flex items-center gap-1 text-caption font-semibold text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {t('writing.editor.back')}
        </button>
        <h1 className="min-w-0 flex-1 truncate text-center text-body font-semibold text-ink">
          {t(`practice.taskType.${submission.taskType}`)}
          {revision && ` · ${t(`writing.version.${draft.versionNo}`)}`}
        </h1>
        {!revision && (
          <span
            role="timer"
            aria-live="off"
            aria-label={clock.overtime ? t('writing.editor.overtime') : t('practice.player.timer')}
            className={cn(
              'inline-flex items-center gap-1 rounded-lg bg-surface-sunken px-2 py-1 font-mono text-body tabular-nums',
              clock.overtime ? 'text-warning' : 'text-ink',
            )}
          >
            <Timer className="size-4" aria-hidden="true" />
            {clock.text}
            {clock.overtime && (
              <span className="font-sans text-caption font-semibold">{t('writing.editor.overtime')}</span>
            )}
          </span>
        )}
      </header>

      <div
        className={cn(
          'grid gap-6 bg-surface-task p-4 md:p-6',
          revision ? 'lg:grid-cols-[minmax(0,1fr)_320px]' : 'lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]',
        )}
      >
        {revision ? (
          <>
            <div className="flex min-w-0 flex-col gap-4">
              <details className="rounded-xl border border-border bg-surface p-4">
                <summary className="cursor-pointer text-body font-semibold text-ink">
                  {t('writing.editor.showTask')}
                </summary>
                <div className="mt-3">
                  <TaskCard taskType={submission.taskType} stimulus={stimulus} />
                </div>
              </details>
              {editor}
            </div>
            <aside aria-labelledby="previous-comments" className="flex flex-col gap-3">
              <h2 id="previous-comments" className="text-body font-semibold text-ink">
                {t('writing.editor.previousComments', {
                  version: t(`writing.version.${draft.versionNo - 1}`),
                })}
              </h2>
              {previousComments.length === 0 && (
                <p className="text-caption text-ink-muted">{t('writing.review.noComments')}</p>
              )}
              {previousComments.map((comment) => (
                <CommentCard key={comment.id} comment={comment} />
              ))}
            </aside>
          </>
        ) : (
          <>
            <aside aria-label={t('writing.editor.task')} className="min-w-0">
              <TaskCard taskType={submission.taskType} stimulus={stimulus} />
            </aside>
            {editor}
          </>
        )}
      </div>

      <footer className="flex flex-wrap items-center gap-3 border-t border-border px-4 py-3">
        <span aria-live="polite" className="text-caption text-ink-muted">
          {saveState === 'saving' && t('practice.player.saving')}
          {saveState === 'saved' && t('practice.player.saved')}
          {saveState === 'error' && t('writing.editor.saveFailed')}
        </span>
        <Button className="ml-auto" onClick={requestSubmit} disabled={busy || words === 0}>
          {busy ? t('writing.editor.submitting') : t('writing.editor.submit')}
        </Button>
        {failed && (
          <p role="alert" className="w-full text-right text-caption text-danger">
            {t('practice.player.error')}
          </p>
        )}
      </footer>

      <ConfirmDialog
        open={confirming}
        title={t('writing.editor.shortTitle')}
        confirm={t('writing.editor.submitAnyway')}
        cancel={t('writing.editor.keepWriting')}
        onConfirm={() => void send()}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}
