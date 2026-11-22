import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Check, Crown } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { planTiersQuery } from '@/api/billing';
import { PlanTierCode } from '@/api/generated/billing';
import { Button } from '@/components/ui/button';
import { formatPrice, paywallTitleKey } from '@/lib/billing';
import { onPaywall } from '@/lib/paywall';
import { Modal } from './modal';

const BENEFITS = ['weeks', 'checks', 'mock', 'tutor'] as const;

interface Props {
  entitlement: string | null;
  onClose: () => void;
}

/** PaywallSheet of docs/07 §3.21: a title by the missing right, four benefits, the Pro price, Upgrade and Not now. */
export function Paywall({ entitlement, onClose }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const plans = useQuery({ ...planTiersQuery, enabled: entitlement !== null });
  const pro = plans.data?.items.find((plan) => plan.code === PlanTierCode.StudentPro);

  return (
    <Modal open={entitlement !== null} onClose={onClose} labelledBy="paywall-title" className="glass">
      <span className="grid size-12 place-items-center self-center rounded-full bg-accent-soft text-accent-on-soft">
        <Crown className="size-6" aria-hidden="true" />
      </span>
      <h2 id="paywall-title" className="text-center text-title-2 font-semibold">
        {t(paywallTitleKey(entitlement))}
      </h2>
      <ul className="flex flex-col gap-2 text-body">
        {BENEFITS.map((benefit) => (
          <li key={benefit} className="flex items-start gap-2">
            <Check className="mt-0.5 size-4 flex-none text-brand-accent" aria-hidden="true" />
            {t(`billing.paywall.benefits.${benefit}`)}
          </li>
        ))}
      </ul>
      {pro && (
        <p className="text-center text-title-2 font-semibold text-ink">
          {t('billing.paywall.price', { price: formatPrice(pro.priceMinor, pro.currency) })}
        </p>
      )}
      <div className="flex flex-col gap-2">
        <Button
          block
          onClick={() => {
            onClose();
            void navigate({ to: '/billing' });
          }}
        >
          {t('billing.paywall.upgrade')}
        </Button>
        <Button variant="ghost" block onClick={onClose}>
          {t('billing.paywall.notNow')}
        </Button>
      </div>
      <p className="text-center text-micro text-ink-muted">{t('billing.paywall.footer')}</p>
    </Modal>
  );
}

/** Listens for refused requests (`403 entitlement-required`) anywhere in the app and shows the Paywall. */
export function PaywallHost() {
  const [entitlement, setEntitlement] = useState<string | null>(null);
  useEffect(() => onPaywall((missing) => setEntitlement(missing)), []);
  return <Paywall entitlement={entitlement} onClose={() => setEntitlement(null)} />;
}
