import { useEffect, useRef, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  labelledBy: string;
  className?: string;
  children: ReactNode;
}

/** Native modal dialog: focus trap, Escape and the backdrop come from the browser (docs/07 §3.21 web dialog). */
export function Modal({ open, onClose, labelledBy, className, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby={labelledBy}
      className={cn(
        'm-auto w-[min(480px,94vw)] rounded-[20px] border border-border bg-surface p-0 text-ink shadow-e1 backdrop:bg-black/40',
        className,
      )}
    >
      {open && <div className="flex max-h-[85vh] flex-col gap-4 overflow-auto p-6">{children}</div>}
    </dialog>
  );
}
