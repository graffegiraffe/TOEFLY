import { Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { WeekTaskStatus } from '@/api/generated/planning';
import { cn } from '@/lib/cn';

/** Round status mark of a TaskRow: empty, half for a running attempt, a check when done (docs/07-ux-ui.md §5.5). */
export function TaskStatus({ status }: { status: WeekTaskStatus }) {
  const { t } = useTranslation();
  const label = t(`weeks.status.${status}`);
  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      className={cn(
        'relative grid size-5 flex-none place-items-center overflow-hidden rounded-full border-2',
        status === 'DONE' ? 'border-brand-accent bg-brand-accent text-white' : 'border-border-strong',
        status === 'SKIPPED' && 'border-dashed',
      )}
    >
      {status === 'DONE' && <Check className="size-3" strokeWidth={3} aria-hidden="true" />}
      {status === 'IN_PROGRESS' && (
        <span className="absolute inset-y-0 left-0 w-1/2 bg-brand-accent" aria-hidden="true" />
      )}
    </span>
  );
}
