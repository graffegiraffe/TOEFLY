import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Modal } from './modal';

interface Props {
  open: boolean;
  plan: string;
  until: string;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/** "You'll keep Pro until Dec 1" (AC-27.05.2): the plan stays until the end of the paid period. */
export function CancelDialog({ open, plan, until, pending, onConfirm, onClose }: Props) {
  const { t } = useTranslation();
  return (
    <Modal open={open} onClose={onClose} labelledBy="cancel-title">
      <h2 id="cancel-title" className="text-title-3 font-semibold">
        {t('billing.cancel.title')}
      </h2>
      <p className="text-body text-ink-muted">{t('billing.cancel.body', { plan, date: until })}</p>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          {t('billing.cancel.keep', { plan })}
        </Button>
        <Button variant="destructive" onClick={onConfirm} disabled={pending}>
          {t('billing.cancel.confirm')}
        </Button>
      </div>
    </Modal>
  );
}
