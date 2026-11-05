interface ProgressBarProps {
  value: number;
  label: string;
}

export function ProgressBar({ value, label }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={clamped}
      className="h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken"
    >
      <div className="h-full origin-left rounded-full bg-bar-gradient" style={{ width: `${clamped}%` }} />
    </div>
  );
}
