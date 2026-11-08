import { useTranslation } from 'react-i18next';
import type { ItemStatus } from '@/api/generated/content';
import { cn } from '@/lib/cn';

/** Status as text plus colour: colour alone never carries the meaning. */
export function StatusBadge({ status }: { status: ItemStatus }) {
  const { t } = useTranslation();
  const approved = status === 'APPROVED';
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2 py-0.5 text-micro font-semibold',
        approved
          ? 'border-transparent bg-accent-soft text-accent-on-soft'
          : 'border-border bg-surface-sunken text-ink-muted',
      )}
    >
      {t(`admin.status.${status}`)}
    </span>
  );
}
