import { Check, CircleAlert, ListPlus, LoaderCircle, RotateCcw, Sparkles } from 'lucide-react';
import { Fragment } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/cn';
import { parseMarkdown, type Inline } from '@/lib/tutor/markdown';
import { chatErrorKind } from '@/lib/tutor/problems';
import type { ReplyAction, ReplyProblem, ReplyStatus, ReplyTool } from '@/lib/tutor/reply';

/** ChatBubble of docs/07-ux-ui.md §3.16: the learner on the right on accent-soft, the tutor full width. */
export function UserBubble({ text }: { text: string }) {
  return (
    <div className="max-w-[80%] self-end whitespace-pre-wrap break-words rounded-[16px_16px_6px_16px] bg-accent-soft px-3.5 py-2.5 text-body text-ink">
      {text}
    </div>
  );
}

function InlineText({ parts }: { parts: Inline[] }) {
  return (
    <>
      {parts.map((part, i) => {
        if (part.kind === 'strong') return <strong key={i}>{part.text}</strong>;
        if (part.kind === 'em') return <em key={i}>{part.text}</em>;
        if (part.kind === 'code')
          return (
            <code key={i} className="rounded-[5px] bg-surface-sunken px-1 py-px text-caption">
              {part.text}
            </code>
          );
        return <Fragment key={i}>{part.text}</Fragment>;
      })}
    </>
  );
}

/** The Markdown subset of an answer, rendered without HTML. */
export function MarkdownText({ text, caret }: { text: string; caret?: boolean }) {
  const blocks = parseMarkdown(text);
  const last = blocks.length - 1;
  const cursor = caret ? (
    <span
      className="ml-0.5 inline-block h-4 w-2 bg-brand-accent align-[-3px] motion-safe:animate-pulse"
      aria-hidden="true"
      data-testid="caret"
    />
  ) : null;
  if (blocks.length === 0) return cursor;
  return (
    <div className="flex flex-col gap-2 text-body text-ink">
      {blocks.map((block, i) =>
        block.kind === 'p' ? (
          <p key={i} className="break-words">
            <InlineText parts={block.inline} />
            {i === last && cursor}
          </p>
        ) : (
          (() => {
            const List = block.kind;
            return (
              <List key={i} className={cn('flex flex-col gap-1 pl-5', List === 'ul' ? 'list-disc' : 'list-decimal')}>
                {block.items.map((item, j) => (
                  <li key={j}>
                    <InlineText parts={item} />
                    {i === last && j === block.items.length - 1 && cursor}
                  </li>
                ))}
              </List>
            );
          })()
        ),
      )}
    </div>
  );
}

export type ActionState = ReplyAction['status'] | 'CONFIRMING';

interface AssistantBubbleProps {
  text: string;
  status: ReplyStatus;
  tools?: ReplyTool[];
  actions?: ReplyAction[];
  actionStates?: Record<string, ActionState>;
  problem?: ReplyProblem | null;
  onConfirm?: (actionId: string) => void;
  onRetry?: () => void;
  /** Language of the explanation: RU answers are marked for screen readers. */
  lang?: string | null;
}

export function AssistantBubble({
  text,
  status,
  tools = [],
  actions = [],
  actionStates = {},
  problem,
  onConfirm,
  onRetry,
  lang,
}: AssistantBubbleProps) {
  const { t } = useTranslation();
  const streaming = status === 'streaming' || status === 'reconnecting';
  return (
    <article className="flex flex-col gap-1.5" aria-label={t('tutor.name')} data-testid="tutor-answer">
      <div className="flex items-center gap-1.5 text-caption font-semibold text-ink">
        <Sparkles className="size-3.5 text-brand-accent" aria-hidden="true" />
        {t('tutor.name')}
      </div>
      {tools.map((tool, i) => (
        <p
          key={`${tool.name}-${i}`}
          className="flex items-center gap-1.5 text-caption text-ink-muted"
          data-testid="tool-line"
        >
          {tool.finished ? (
            <Check className="size-3.5" aria-hidden="true" />
          ) : (
            <LoaderCircle className="size-3.5 motion-safe:animate-spin" aria-hidden="true" />
          )}
          {tool.label}
        </p>
      ))}
      {status === 'thinking' && !text ? (
        <span className="inline-flex items-center gap-1 text-caption text-ink-muted" data-testid="thinking">
          <span className="size-1.5 rounded-full bg-ink-subtle motion-safe:animate-pulse" aria-hidden="true" />
          <span
            className="size-1.5 rounded-full bg-ink-subtle motion-safe:animate-pulse [animation-delay:200ms]"
            aria-hidden="true"
          />
          <span
            className="size-1.5 rounded-full bg-ink-subtle motion-safe:animate-pulse [animation-delay:400ms]"
            aria-hidden="true"
          />
          <span className="ml-1">{t('tutor.thinking')}</span>
        </span>
      ) : (
        <div lang={lang === 'RU' ? 'ru' : undefined} data-testid="answer-text">
          <MarkdownText text={text} caret={streaming} />
        </div>
      )}
      {actions.map((action) => (
        <ActionCard
          key={action.actionId}
          action={action}
          state={actionStates[action.actionId] ?? action.status}
          onConfirm={onConfirm}
        />
      ))}
      {status === 'reconnecting' && <p className="text-caption text-ink-muted">{t('tutor.reconnecting')}</p>}
      {status === 'stopped' && <p className="text-caption text-ink-muted">{t('tutor.stopped')}</p>}
      {status === 'interrupted' && (
        <div className="flex flex-wrap items-center gap-2 text-caption text-ink-muted">
          <span>{t('tutor.interrupted')}</span>
          {onRetry && (
            <Button variant="secondary" className="h-8 px-3 text-caption" onClick={onRetry}>
              <RotateCcw className="size-3.5" aria-hidden="true" />
              {t('tutor.retry')}
            </Button>
          )}
        </div>
      )}
      {status === 'failed' && (
        <p className="flex items-start gap-1.5 text-caption text-danger" role="alert">
          <CircleAlert className="mt-0.5 size-3.5 flex-none" aria-hidden="true" />
          {t(`tutor.errors.${problem ? chatErrorKind(problem) : 'generic'}`)}
        </p>
      )}
    </article>
  );
}

function ActionCard({
  action,
  state,
  onConfirm,
}: {
  action: ReplyAction;
  state: ActionState;
  onConfirm?: (actionId: string) => void;
}) {
  const { t } = useTranslation();
  return (
    <div
      className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-surface p-3"
      data-testid="action-card"
    >
      <span className="grid size-8 flex-none place-items-center rounded-lg bg-accent-soft text-accent-on-soft">
        <ListPlus className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1 text-body font-semibold text-ink">{action.title}</span>
      {state === 'CONFIRMED' ? (
        <span className="flex items-center gap-1 text-caption font-semibold text-success">
          <Check className="size-3.5" aria-hidden="true" />
          {t('tutor.action.confirmed')}
        </span>
      ) : state === 'EXPIRED' ? (
        <span className="text-caption text-ink-muted">{t('tutor.action.expired')}</span>
      ) : (
        <Button
          className="h-9 px-4 text-caption"
          disabled={state === 'CONFIRMING' || !onConfirm}
          onClick={() => onConfirm?.(action.actionId)}
        >
          {state === 'CONFIRMING' ? t('tutor.action.confirming') : t('tutor.action.confirm')}
        </Button>
      )}
    </div>
  );
}
