import { useQuery, useQueryClient } from '@tanstack/react-query';
import { MapPin, MessagesSquare, Plus, Send, Sparkles, Square, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ExplanationLanguage, ResponseError, type ChatMessage, type ChatThread } from '@/api/generated/tutoragent';
import {
  ActionExpiredError,
  messagesQuery,
  threadsQuery,
  useConfirmAction,
  useCreateThread,
  useThreadLanguage,
  type ScreenContext,
} from '@/api/tutor';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/cn';
import { chatErrorKind, readProblem, type ChatErrorKind } from '@/lib/tutor/problems';
import type { ReplyAction, ReplyStatus } from '@/lib/tutor/reply';
import { AssistantBubble, UserBubble, type ActionState } from './chat-bubbles';
import { useTutor } from './use-tutor';
import { useChatStream, type StreamOptions } from './use-chat-stream';

export const MAX_MESSAGE = 2000;

const HISTORY_STATUS: Record<string, ReplyStatus> = {
  COMPLETE: 'done',
  STREAMING: 'streaming',
  INTERRUPTED: 'stopped',
  RETRACTED: 'retracted',
  FAILED: 'failed',
};

function suggestionsFor(context: ScreenContext | null): string[] {
  switch (context?.type) {
    case 'ATTEMPT_REVIEW':
      return ['wrong', 'similar', 'grammar'];
    case 'SUBMISSION_REVIEW':
      return ['essay', 'writing', 'grammar'];
    case 'WEEK':
    case 'WEEKLY_REVIEW':
      return ['week', 'focus'];
    default:
      return ['focus', 'writing'];
  }
}

/**
 * The contextual AI Tutor panel (docs/07-ux-ui.md §5.17): a 400px glass panel on the right, a full-screen sheet on
 * narrow screens. It stays mounted while closed, so an answer keeps streaming when the panel is hidden.
 */
export function TutorPanel({ streamOptions }: { streamOptions?: Omit<StreamOptions, 'onSettled'> }) {
  const tutor = useTutor();
  const client = useQueryClient();
  const stream = useChatStream({
    ...streamOptions,
    onSettled: (threadId) => {
      void client.invalidateQueries({ queryKey: messagesQuery(threadId).queryKey });
      void client.invalidateQueries({ queryKey: threadsQuery.queryKey });
    },
  });
  const { closeTutor } = tutor;

  useEffect(() => {
    if (!tutor.open) return;
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') closeTutor();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [tutor.open, closeTutor]);

  if (tutor.hidden) return null;
  return (
    <aside
      role="dialog"
      aria-labelledby="tutor-title"
      hidden={!tutor.open}
      data-testid="tutor-panel"
      className={cn(
        // Glass is allowed for this panel (docs/07 §2.6); a denser fill keeps the chat readable over any page.
        'glass fixed inset-0 z-40 flex flex-col overflow-hidden text-ink [--glass-fill:var(--surface-raised)]',
        'sm:inset-auto sm:bottom-[14px] sm:right-[14px] sm:top-[14px] sm:w-[400px] sm:rounded-[20px]',
        'sm:[--glass-fill:color-mix(in_srgb,var(--surface-raised)_84%,transparent)]',
      )}
    >
      {tutor.open && <PanelBody key={tutor.request.nonce} stream={stream} />}
    </aside>
  );
}

type Stream = ReturnType<typeof useChatStream>;

function PanelBody({ stream }: { stream: Stream }) {
  const { t } = useTranslation();
  const tutor = useTutor();
  const { threadId, setThreadId } = tutor;
  const [view, setView] = useState<'chat' | 'threads'>('chat');
  const [chip, setChip] = useState<ScreenContext | null>(tutor.request.context);
  const [chipRemoved, setChipRemoved] = useState(false);
  const [draft, setDraft] = useState('');
  const [languageChoice, setLanguageChoice] = useState<ExplanationLanguage | null>(null);
  const [error, setError] = useState<ChatErrorKind | null>(null);
  const [actionStates, setActionStates] = useState<Record<string, ActionState>>({});
  const input = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);

  const threads = useQuery(threadsQuery);
  const messages = useQuery({ ...messagesQuery(threadId ?? 'none'), enabled: Boolean(threadId) });
  const create = useCreateThread();
  const language = useThreadLanguage();
  const confirm = useConfirmAction();

  const thread: ChatThread | undefined = threads.data?.items.find((item) => item.threadId === threadId);
  const currentLanguage = languageChoice ?? thread?.language ?? ExplanationLanguage.En;
  const history: ChatMessage[] = useMemo(
    () => (threadId ? (messages.data?.items ?? []) : []),
    [threadId, messages.data],
  );
  const historyIds = useMemo(() => new Set(history.map((message) => message.messageId)), [history]);
  const turns = stream.turns.filter(
    (turn) => turn.threadId === threadId && !(turn.reply.messageId && historyIds.has(turn.reply.messageId)),
  );
  const empty = history.length === 0 && turns.length === 0;
  const lastTurn = turns.at(-1);
  const lastOwn = stream.turns.filter((turn) => turn.threadId === threadId).at(-1);

  useEffect(() => {
    input.current?.focus();
  }, []);

  useEffect(() => {
    const element = log.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [history.length, lastTurn?.reply.text, lastTurn?.reply.status, turns.length]);

  const problemKind = async (caught: unknown): Promise<ChatErrorKind> =>
    caught instanceof ResponseError ? chatErrorKind(await readProblem(caught.response)) : 'generic';

  const submit = async (raw: string) => {
    const text = raw.trim();
    if (!text || stream.streaming || create.isPending) return;
    setError(null);
    let id = threadId;
    let context: ScreenContext | null | undefined;
    if (!id) {
      try {
        const created = await create.mutateAsync({ context: chip });
        id = created.threadId;
        if (currentLanguage !== created.language) {
          await language.mutateAsync({ threadId: id, language: currentLanguage }).catch(() => undefined);
        }
        setThreadId(id);
      } catch (caught) {
        setError(await problemKind(caught));
        return;
      }
      context = undefined; // the thread carries the context
    } else {
      context = chip ?? (chipRemoved ? null : undefined);
    }
    setDraft('');
    stream.send(id, text, context);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void submit(draft);
    }
  };

  const changeLanguage = (next: ExplanationLanguage) => {
    setLanguageChoice(next);
    if (threadId) language.mutate({ threadId, language: next });
  };

  const confirmAction = (actionId: string, turnKey?: string) => {
    setActionStates((all) => ({ ...all, [actionId]: 'CONFIRMING' }));
    confirm.mutate(actionId, {
      onSuccess: (result) => {
        const status = result.status === 'CONFIRMED' ? 'CONFIRMED' : 'EXPIRED';
        setActionStates((all) => ({ ...all, [actionId]: status }));
        if (turnKey) stream.setActionStatus(turnKey, actionId, status);
      },
      onError: (caught) =>
        setActionStates((all) => ({
          ...all,
          [actionId]: caught instanceof ActionExpiredError ? 'EXPIRED' : 'PENDING',
        })),
    });
  };

  const counterId = 'tutor-counter';
  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex items-center gap-2 px-4 pb-2 pt-4">
        <Sparkles className="size-[18px] text-brand-accent" aria-hidden="true" />
        <h2 id="tutor-title" className="flex-1 text-title-3 font-semibold text-ink">
          {t('tutor.name')}
        </h2>
        <div role="group" aria-label={t('tutor.language')} className="flex rounded-full bg-surface-sunken p-0.5">
          {[ExplanationLanguage.En, ExplanationLanguage.Ru].map((code) => (
            <button
              key={code}
              type="button"
              aria-pressed={currentLanguage === code}
              title={t(code === ExplanationLanguage.En ? 'tutor.languageEnFull' : 'tutor.languageRuFull')}
              aria-label={t(code === ExplanationLanguage.En ? 'tutor.languageEnFull' : 'tutor.languageRuFull')}
              onClick={() => changeLanguage(code)}
              className={cn(
                'h-7 min-w-9 rounded-full px-2 text-caption font-semibold',
                currentLanguage === code ? 'bg-surface text-ink shadow-e1' : 'text-ink-muted hover:text-ink',
              )}
            >
              {t(code === ExplanationLanguage.En ? 'tutor.languageEn' : 'tutor.languageRu')}
            </button>
          ))}
        </div>
        <button
          type="button"
          aria-pressed={view === 'threads'}
          aria-label={t('tutor.chats')}
          title={t('tutor.chats')}
          onClick={() => setView(view === 'threads' ? 'chat' : 'threads')}
          className="grid size-9 place-items-center rounded-lg text-ink-muted hover:bg-surface-sunken hover:text-ink aria-pressed:bg-surface-sunken aria-pressed:text-ink"
        >
          <MessagesSquare className="size-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label={t('tutor.close')}
          title={t('tutor.close')}
          onClick={tutor.closeTutor}
          className="grid size-9 place-items-center rounded-lg text-ink-muted hover:bg-surface-sunken hover:text-ink"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      </header>

      {view === 'threads' ? (
        <nav aria-label={t('tutor.chats')} className="flex min-h-0 flex-1 flex-col gap-2 overflow-auto px-4 pb-4">
          <Button
            className="self-start"
            onClick={() => {
              setThreadId(null);
              setView('chat');
            }}
          >
            <Plus className="size-4" aria-hidden="true" />
            {t('tutor.newChat')}
          </Button>
          {threads.data && threads.data.items.length === 0 && (
            <p className="text-caption text-ink-muted">{t('tutor.noChats')}</p>
          )}
          <ul className="flex flex-col gap-1">
            {threads.data?.items.map((item) => (
              <li key={item.threadId}>
                <button
                  type="button"
                  aria-current={item.threadId === threadId ? 'true' : undefined}
                  onClick={() => {
                    setThreadId(item.threadId);
                    setView('chat');
                  }}
                  className="flex w-full flex-col items-start rounded-lg px-3 py-2 text-left hover:bg-surface-sunken aria-[current=true]:bg-surface-sunken"
                >
                  <span className="w-full truncate text-body font-semibold text-ink">{item.title}</span>
                  <span className="text-caption text-ink-muted">
                    {new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' }).format(
                      item.lastMessageAt ?? item.createdAt,
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </nav>
      ) : (
        <>
          {chip && (
            <div className="px-4 pb-2">
              <span
                className="inline-flex h-[26px] max-w-full items-center gap-1.5 rounded-full bg-surface-sunken pl-2.5 pr-1 text-caption font-semibold text-ink-muted"
                data-testid="context-chip"
              >
                <MapPin className="size-3 flex-none" aria-hidden="true" />
                <span className="truncate">{t('tutor.contextChip', { label: chip.label })}</span>
                <button
                  type="button"
                  aria-label={t('tutor.removeContext')}
                  title={t('tutor.removeContext')}
                  onClick={() => {
                    setChip(null);
                    setChipRemoved(true);
                    input.current?.focus();
                  }}
                  className="grid size-5 flex-none place-items-center rounded-full hover:bg-surface hover:text-ink"
                >
                  <X className="size-3" aria-hidden="true" />
                </button>
              </span>
            </div>
          )}
          <div ref={log} className="min-h-0 flex-1 overflow-auto px-4 pb-3 pt-1.5" data-testid="chat-log">
            {threadId && messages.isPending ? (
              <div className="flex flex-col gap-4" aria-busy="true">
                <span className="sr-only">{t('tutor.loading')}</span>
                <div className="h-10 w-3/5 self-end rounded-2xl bg-surface-sunken" />
                <div className="h-16 rounded-2xl bg-surface-sunken" />
              </div>
            ) : (
              <div className="flex flex-col gap-4">
                {empty && <p className="text-body text-ink-muted">{t('tutor.intro')}</p>}
                {history.map((message) =>
                  message.role === 'USER' ? (
                    <UserBubble key={message.messageId} text={message.text} />
                  ) : (
                    <AssistantBubble
                      key={message.messageId}
                      text={message.text}
                      status={HISTORY_STATUS[message.status] ?? 'done'}
                      actions={(message.actions ?? []) as ReplyAction[]}
                      actionStates={actionStates}
                      onConfirm={(actionId) => confirmAction(actionId)}
                      lang={thread?.language}
                    />
                  ),
                )}
                {turns.map((turn) => (
                  <div key={turn.key} className="flex flex-col gap-4">
                    <UserBubble text={turn.text} />
                    <AssistantBubble
                      text={turn.reply.text}
                      status={turn.reply.status}
                      tools={turn.reply.tools}
                      actions={turn.reply.actions}
                      actionStates={actionStates}
                      problem={turn.reply.problem}
                      lang={turn.reply.language}
                      onConfirm={(actionId) => confirmAction(actionId, turn.key)}
                      onRetry={() => stream.retry(turn.key, turn.reply.messageId, turn.reply.lastEventId)}
                    />
                  </div>
                ))}
              </div>
            )}
            <p className="sr-only" role="status">
              {lastOwn?.reply.status === 'done' ? t('tutor.answered') : ''}
            </p>
          </div>
          <div className="flex flex-col gap-2 px-4 pb-4 pt-2">
            {empty && (
              <div className="flex flex-wrap gap-2" role="group" aria-label={t('tutor.suggestions')}>
                {suggestionsFor(chip).map((key) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => void submit(t(`tutor.suggest.${key}`))}
                    className="inline-flex h-8 items-center rounded-full border border-border-strong bg-surface px-3 text-caption font-medium text-ink hover:border-brand-accent"
                  >
                    {t(`tutor.suggest.${key}`)}
                  </button>
                ))}
              </div>
            )}
            {error && (
              <p className="text-caption text-danger" role="alert">
                {t(`tutor.errors.${error}`)}
              </p>
            )}
            <div className="flex items-end gap-2 rounded-[22px] border border-border-strong bg-surface p-2 focus-within:outline-2 focus-within:outline-focus-ring">
              <textarea
                ref={input}
                rows={1}
                value={draft}
                maxLength={MAX_MESSAGE}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={onKeyDown}
                aria-label={t('tutor.message')}
                aria-describedby={counterId}
                placeholder={t('tutor.placeholder')}
                className="max-h-32 min-h-9 flex-1 resize-none bg-transparent px-1.5 py-2 text-body text-ink outline-none [field-sizing:content] placeholder:text-ink-muted"
              />
              {stream.streaming ? (
                <Button variant="secondary" className="h-9 px-3" onClick={stream.stop}>
                  <Square className="size-3.5" aria-hidden="true" />
                  {t('tutor.stop')}
                </Button>
              ) : (
                <Button
                  className="h-9 px-3"
                  disabled={!draft.trim() || create.isPending}
                  onClick={() => void submit(draft)}
                >
                  <Send className="size-3.5" aria-hidden="true" />
                  {t('tutor.send')}
                </Button>
              )}
            </div>
            <p
              id={counterId}
              className={cn(
                'self-end text-micro',
                draft.length >= MAX_MESSAGE ? 'font-semibold text-danger' : 'text-ink-muted',
              )}
              data-testid="message-counter"
            >
              {t('tutor.counter', { count: draft.length, max: MAX_MESSAGE })}
            </p>
          </div>
        </>
      )}
    </div>
  );
}
