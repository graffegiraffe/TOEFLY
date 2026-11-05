import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  text?: string;
  action?: ReactNode;
}

/** Centered block with a line illustration, title, text and one action (docs/07-ux-ui.md §3.18). */
export function EmptyState({ title, text, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
      <svg width="120" height="80" viewBox="0 0 120 80" fill="none" aria-hidden="true" className="text-brand-accent">
        <path d="M10 70 C 35 20, 85 20, 110 70" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <circle cx="60" cy="32" r="8" stroke="currentColor" strokeWidth="2" />
        <path d="M30 70h60" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <h2 className="text-title-3 font-semibold text-ink">{title}</h2>
      {text && <p className="max-w-md text-body text-ink-muted">{text}</p>}
      {action}
    </div>
  );
}
