import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { paragraphPieces, type Highlight, type Marker } from '@/lib/practice/passage';
import { cn } from '@/lib/cn';
import type { Stimulus } from './content';

interface PassageProps {
  stimulus: Stimulus;
  highlight?: Highlight | null;
  markers?: Marker[];
  /** The sentence to insert and the chosen marker (AC-07.03.3). */
  insertion?: { sentence: string; chosen: string | null; onChoose: (key: string) => void } | null;
}

/** Academic passage (US-07.03): title, paragraphs, the highlighted word scrolled into view, insert markers. */
export function Passage({ stimulus, highlight, markers, insertion }: PassageProps) {
  const { t } = useTranslation();
  const mark = useRef<HTMLElement>(null);

  useEffect(() => {
    mark.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  }, [highlight?.paragraph, highlight?.charStart]);

  return (
    <article className="flex flex-col gap-4 text-body-lg leading-relaxed text-ink">
      {stimulus.title && <h2 className="text-title-3 font-semibold">{stimulus.title}</h2>}
      {(stimulus.paragraphs ?? []).map((text, p) => (
        <p key={p}>
          {paragraphPieces(text, p, insertion ? markers : [], highlight).map((piece, i) => {
            if (piece.kind === 'marker') {
              const chosen = insertion?.chosen === piece.key;
              return (
                <span key={i}>
                  {' '}
                  <button
                    type="button"
                    aria-pressed={chosen}
                    aria-label={t('practice.passage.marker', { key: piece.key })}
                    onClick={() => insertion?.onChoose(piece.key)}
                    className={cn(
                      'mx-0.5 inline-grid size-5 place-items-center rounded-[3px] align-middle text-[0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring',
                      chosen ? 'bg-primary' : 'bg-ink hover:bg-primary',
                    )}
                  >
                    ■
                  </button>
                  {chosen && insertion && (
                    <span className="rounded bg-accent-soft px-1 font-medium text-accent-on-soft">
                      {insertion.sentence}
                    </span>
                  )}
                </span>
              );
            }
            return piece.highlight ? (
              <mark key={i} ref={mark} className="rounded bg-accent-soft px-0.5 text-accent-on-soft">
                {piece.text}
              </mark>
            ) : (
              <span key={i}>{piece.text}</span>
            );
          })}
        </p>
      ))}
    </article>
  );
}

/** Daily-life text in the frame of its genre (AC-07.02.1). */
export function DailyLifeText({ stimulus }: { stimulus: Stimulus }) {
  const { t } = useTranslation();
  const genre = stimulus.genre ?? 'NOTICE';
  if (genre === 'CHAT' || (stimulus.messages && stimulus.messages.length > 0)) {
    return (
      <section aria-label={stimulus.title} className="flex flex-col gap-2">
        {stimulus.title && <h2 className="text-title-3 font-semibold text-ink">{stimulus.title}</h2>}
        {(stimulus.messages ?? []).map((message, i) => (
          <div key={i} className={cn('flex flex-col gap-0.5', i % 2 === 1 && 'items-end')}>
            <span className="text-micro text-ink-muted">
              {message.sender}
              {message.time ? ` · ${message.time}` : ''}
            </span>
            <p
              className={cn(
                'max-w-[85%] rounded-2xl px-3 py-2 text-body text-ink',
                i % 2 === 1 ? 'rounded-br-sm bg-accent-soft' : 'rounded-bl-sm bg-surface-sunken',
              )}
            >
              {message.text}
            </p>
          </div>
        ))}
      </section>
    );
  }
  const headers = stimulus.headers ?? {};
  const email = genre === 'EMAIL';
  return (
    <article
      className={cn(
        'flex flex-col gap-3 rounded-xl bg-surface p-5 text-body text-ink',
        email ? 'border border-border' : 'border-2 border-border-strong',
      )}
    >
      {email && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 border-b border-border pb-3 text-caption">
          {Object.entries(headers).map(([name, value]) => (
            <div key={name} className="contents">
              <dt className="font-semibold text-ink-muted">{headerLabel(name, t)}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}
      {stimulus.title && <h2 className="text-title-3 font-semibold">{stimulus.title}</h2>}
      {(stimulus.body ?? '').split(/\n+/).map((line, i) => (
        <p key={i} className="whitespace-pre-line">
          {line}
        </p>
      ))}
    </article>
  );
}

function headerLabel(name: string, t: (key: string) => string): string {
  const key = name.toLowerCase();
  if (key === 'from' || key === 'to' || key === 'subject') return t(`practice.passage.${key}`);
  return name;
}
