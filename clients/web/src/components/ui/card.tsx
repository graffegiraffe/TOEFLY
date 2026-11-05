import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-xl border border-border bg-surface p-6 shadow-e1', className)} {...props} />;
}

/** Glass is for the frame of the interface only (docs/07-ux-ui.md §2.6). */
export function GlassPanel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('glass rounded-[20px]', className)} {...props} />;
}
