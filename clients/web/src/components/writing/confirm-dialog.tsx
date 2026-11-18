import { useEffect, useRef } from 'react';
import { Button } from '@/components/ui/button';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  text?: string;
  confirm: string;
  cancel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/** A modal question with two answers on the native `<dialog>` (focus trap and Escape come with it). */
export function ConfirmDialog({ open, title, text, confirm, cancel, onConfirm, onCancel }: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      onClose={onCancel}
      aria-labelledby="confirm-title"
      aria-describedby={text ? 'confirm-text' : undefined}
      className="m-auto w-[min(420px,94vw)] rounded-xl border border-border bg-surface p-6 text-ink shadow-modal backdrop:bg-black/40"
    >
      <h2 id="confirm-title" className="text-title-3 font-semibold">
        {title}
      </h2>
      {text && (
        <p id="confirm-text" className="mt-2 text-body text-ink-muted">
          {text}
        </p>
      )}
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>
          {cancel}
        </Button>
        <Button onClick={onConfirm}>{confirm}</Button>
      </div>
    </dialog>
  );
}
