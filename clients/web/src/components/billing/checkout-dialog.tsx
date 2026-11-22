import { CreditCard, FlaskConical } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useCreateCheckout } from '@/api/billing';
import {
  ProviderMode,
  ResponseError,
  type PaymentProviderCode,
  type PaymentProviderOption,
  type PlanTier,
} from '@/api/generated/billing';
import { Button } from '@/components/ui/button';
import { formatPrice, planNameKey, rememberCheckout } from '@/lib/billing';
import { cn } from '@/lib/cn';
import { Modal } from './modal';

interface Props {
  plan: PlanTier | null;
  providers: PaymentProviderOption[];
  onClose: () => void;
  /** Leaves for the provider's hosted page; replaced in tests. */
  navigate?: (url: string) => void;
}

/** Remount it per plan (`key`) so the choice starts fresh. Provider choice before the hosted checkout (AC-27.03.1); the test-mode badge of AC-27.01.3. */
export function CheckoutDialog({ plan, providers, onClose, navigate = (url) => window.location.assign(url) }: Props) {
  const { t } = useTranslation();
  const checkout = useCreateCheckout();
  const [provider, setProvider] = useState<PaymentProviderCode | null>(providers[0]?.code ?? null);
  const [error, setError] = useState<string | null>(null);

  const selected = providers.find((option) => option.code === provider);
  const planName = plan ? t(planNameKey(plan.code)) : '';

  const pay = async () => {
    if (!plan || !provider) return;
    setError(null);
    try {
      const session = await checkout.mutateAsync({ planTierCode: plan.code, provider });
      rememberCheckout(session.id);
      navigate(session.redirectUrl);
    } catch (failure) {
      const conflict = failure instanceof ResponseError && failure.response.status === 409;
      setError(t(conflict ? 'billing.checkout.exists' : 'billing.checkout.failed'));
    }
  };

  return (
    <Modal open={plan !== null} onClose={onClose} labelledBy="checkout-title">
      {plan && (
        <>
          <header className="flex flex-col gap-1">
            <h2 id="checkout-title" className="text-title-3 font-semibold">
              {t('billing.checkout.title', { plan: planName })}
            </h2>
            <p className="text-caption text-ink-muted">
              {t('billing.checkout.subtitle', { price: formatPrice(plan.priceMinor, plan.currency) })}
            </p>
          </header>
          {providers.length === 0 ? (
            <p className="text-body text-ink-muted">{t('billing.checkout.noProviders')}</p>
          ) : (
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-1 text-caption font-semibold uppercase tracking-wide text-ink-muted">
                {t('billing.checkout.method')}
              </legend>
              {providers.map((option) => (
                <label
                  key={option.code}
                  className={cn(
                    'flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 text-body',
                    option.code === provider
                      ? 'border-brand-accent bg-accent-soft'
                      : 'border-border hover:bg-surface-sunken',
                  )}
                >
                  <input
                    type="radio"
                    name="provider"
                    value={option.code}
                    checked={option.code === provider}
                    onChange={() => setProvider(option.code)}
                    className="accent-[var(--brand-accent)]"
                  />
                  <CreditCard className="size-4 text-ink-muted" aria-hidden="true" />
                  <span className="flex-1 font-medium">{t(`billing.providers.${option.code}`)}</span>
                </label>
              ))}
            </fieldset>
          )}
          {selected?.mode === ProviderMode.Test && (
            <p
              data-testid="test-mode-badge"
              className="inline-flex items-center gap-2 self-start rounded-full bg-warning-soft px-3 py-1 text-caption font-semibold text-warning"
            >
              <FlaskConical className="size-3.5" aria-hidden="true" />
              {t('billing.checkout.testMode')}
            </p>
          )}
          {error && (
            <p role="alert" className="text-caption text-danger">
              {error}
            </p>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="ghost" onClick={onClose}>
              {t('billing.checkout.cancel')}
            </Button>
            <Button onClick={() => void pay()} disabled={!provider || checkout.isPending}>
              {checkout.isPending ? t('billing.checkout.redirecting') : t('billing.checkout.continue')}
            </Button>
          </div>
        </>
      )}
    </Modal>
  );
}
