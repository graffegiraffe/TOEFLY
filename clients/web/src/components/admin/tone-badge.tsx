import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import type { Tone } from '@/lib/admin-ai';

const TONES: Record<Tone, string> = {
  neutral: 'border-border bg-surface-sunken text-ink-muted',
  accent: 'border-transparent bg-accent-soft text-accent-on-soft',
  success: 'border-transparent bg-success-soft text-success',
  warning: 'border-transparent bg-warning-soft text-warning',
  danger: 'border-transparent bg-danger-soft text-danger',
};

/** A state as text plus colour: colour alone never carries the meaning. */
export function ToneBadge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center whitespace-nowrap rounded-full border px-2 py-0.5 text-micro font-semibold',
        TONES[tone],
      )}
    >
      {children}
    </span>
  );
}
