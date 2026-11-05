import { cn } from '@/lib/cn';

/** TOE + FLY in brand green; on light glass FLY uses brand-800, which keeps 4.5:1 over the mesh (docs/07-ux-ui.md §4.2). */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('text-[18px] font-bold tracking-tight text-ink', className)} aria-label="TOEFLY">
      TOE<span className="text-brand-800 dark:text-brand-500">FLY</span>
    </span>
  );
}
