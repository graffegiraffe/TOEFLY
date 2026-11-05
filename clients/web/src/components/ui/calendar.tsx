import { DayPicker } from 'react-day-picker';
import { enGB } from 'react-day-picker/locale';
import { cn } from '@/lib/cn';
import { formatDate } from '@/lib/dates';

interface CalendarProps {
  label: string;
  value: Date | undefined;
  onChange: (date: Date | undefined) => void;
  min: Date;
  max: Date;
  disabled?: boolean;
  describedBy?: string;
}

/**
 * Month calendar of docs/07-ux-ui.md §5.3 (exam date): English labels, weeks from Monday, month and year dropdowns
 * in the caption; only days between min and max can be chosen and the navigation stays inside them.
 */
export function Calendar({ label, value, onChange, min, max, disabled = false, describedBy }: CalendarProps) {
  return (
    <div
      role="group"
      aria-label={label}
      aria-describedby={describedBy}
      className={cn(
        'w-fit rounded-xl border border-border bg-surface p-3 text-ink shadow-e1',
        disabled && 'pointer-events-none opacity-50',
      )}
    >
      <p className="px-1 pb-2 text-caption font-semibold text-ink" aria-live="polite" data-testid="calendar-value">
        {value ? formatDate(value) : 'No date selected'}
      </p>
      <DayPicker
        mode="single"
        captionLayout="dropdown"
        locale={enGB}
        weekStartsOn={1}
        selected={value}
        onSelect={onChange}
        defaultMonth={value ?? min}
        startMonth={min}
        endMonth={max}
        disabled={[{ before: min }, { after: max }]}
        showOutsideDays
        classNames={{
          root: 'relative',
          months: 'flex flex-col',
          month: 'flex flex-col gap-2',
          month_caption: 'flex h-9 items-center pr-20',
          caption_label: 'hidden',
          dropdowns: 'flex items-center gap-1.5',
          dropdown_root: 'relative',
          dropdown:
            'h-9 rounded-lg border border-input bg-surface px-2 text-body font-semibold text-ink focus-visible:outline-2 focus-visible:outline-focus-ring',
          nav: 'absolute right-0 top-0 flex gap-1',
          button_previous:
            'grid size-9 place-items-center rounded-lg text-ink-muted hover:bg-surface-sunken hover:text-ink disabled:opacity-30 focus-visible:outline-2 focus-visible:outline-focus-ring',
          button_next:
            'grid size-9 place-items-center rounded-lg text-ink-muted hover:bg-surface-sunken hover:text-ink disabled:opacity-30 focus-visible:outline-2 focus-visible:outline-focus-ring',
          chevron: 'size-4 fill-current',
          month_grid: 'border-collapse',
          weekdays: 'flex',
          weekday: 'w-10 pb-1 text-center text-micro font-semibold uppercase text-ink-muted',
          week: 'flex',
          day: 'p-0.5',
          day_button:
            'grid size-9 place-items-center rounded-lg text-body hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-focus-ring disabled:cursor-not-allowed disabled:hover:bg-transparent',
          today: 'font-bold text-accent-on-soft',
          selected: '[&>button]:bg-primary [&>button]:text-white [&>button]:hover:bg-primary',
          outside: 'text-ink-muted opacity-60',
          disabled: 'text-ink-muted opacity-40',
        }}
      />
    </div>
  );
}
