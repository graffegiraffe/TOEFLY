import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { CircleAlert, CircleCheck, Clock, Loader2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { checkoutSessionQuery, entitlementsQuery } from '@/api/billing';
import { ResponseError } from '@/api/generated/billing';
import { buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { checkoutView, forgetCheckout, keepPolling, planNameKey, POLL_INTERVAL_MS } from '@/lib/billing';

/**
 * Checkout result of docs/07 §5.20 (AC-27.03.4, AC-27.03.5): polls the session every 2 s for up to 60 s. Rights are
 * granted only by the verified webhook and the saga, so the screen waits for COMPLETED and then refreshes them.
 */
export function CheckoutResult({ sessionId }: { sessionId: string | null }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [started] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const session = useQuery({
    ...checkoutSessionQuery(sessionId ?? 'none'),
    enabled: sessionId !== null,
    refetchInterval: (query) =>
      keepPolling(checkoutView(query.state.data, Date.now() - started)) ? POLL_INTERVAL_MS : false,
  });
  const notFound = session.error instanceof ResponseError && session.error.response.status === 404;
  const view = checkoutView(session.data, now - started, sessionId === null || notFound);

  useEffect(() => {
    if (!keepPolling(view)) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [view]);

  const success = view.kind === 'success';
  useEffect(() => {
    if (success) {
      forgetCheckout();
      void queryClient.invalidateQueries({ queryKey: ['billing'] });
    }
  }, [success, queryClient]);

  const entitlements = useQuery({ ...entitlementsQuery, enabled: success });

  return (
    <Card
      className="mx-auto flex w-full max-w-[520px] flex-col items-center gap-4 py-10 text-center"
      data-testid="checkout-result"
    >
      {(view.kind === 'pending' || view.kind === 'slow') && (
        <>
          {view.kind === 'pending' ? (
            <Loader2 className="size-10 animate-spin text-brand-accent motion-reduce:animate-none" aria-hidden="true" />
          ) : (
            <Clock className="size-10 text-ink-muted" aria-hidden="true" />
          )}
          <h1 role="status" className="text-title-2 font-semibold text-ink">
            {view.kind === 'pending' ? t('billing.result.pending') : t('billing.result.slow')}
          </h1>
          <p className="text-body text-ink-muted">
            {view.kind === 'pending' ? t('billing.result.pendingHint') : t('billing.result.slowHint')}
          </p>
          {view.kind === 'slow' && (
            <Link to="/billing" className={buttonClass('secondary')}>
              {t('billing.result.backToBilling')}
            </Link>
          )}
        </>
      )}
      {view.kind === 'success' && (
        <>
          <CircleCheck className="size-12 text-brand-accent" aria-hidden="true" />
          <h1 className="text-title-1 font-semibold text-ink">
            {t('billing.result.success', { plan: t(planNameKey(view.plan)) })}
          </h1>
          {entitlements.data && entitlements.data.entitlements.length > 0 && (
            <div className="flex flex-col items-center gap-2">
              <p className="text-body text-ink-muted">{t('billing.result.unlocked')}</p>
              <ul className="flex flex-col gap-1 text-body text-ink" data-testid="unlocked">
                {entitlements.data.entitlements.map((entitlement) => (
                  <li key={entitlement}>{t(`billing.entitlements.${entitlement}`)}</li>
                ))}
              </ul>
            </div>
          )}
          <Link to="/" className={buttonClass('primary')}>
            {t('billing.result.continue')}
          </Link>
        </>
      )}
      {view.kind === 'failed' && (
        <>
          <CircleAlert className="size-12 text-danger" aria-hidden="true" />
          <h1 className="text-title-2 font-semibold text-ink">{t('billing.result.failed')}</h1>
          {view.message && (
            <p role="alert" className="text-body text-danger" data-testid="failure-message">
              {view.message}
            </p>
          )}
          <p className="text-body text-ink-muted">{t('billing.result.failedHint')}</p>
          <div className="flex flex-wrap justify-center gap-2">
            <Link to="/billing" search={{ retry: session.data?.planTierCode }} className={buttonClass('primary')}>
              {t('billing.result.tryAgain')}
            </Link>
            <Link to="/billing" search={{ retry: session.data?.planTierCode }} className={buttonClass('secondary')}>
              {t('billing.result.otherMethod')}
            </Link>
          </div>
        </>
      )}
      {view.kind === 'missing' && (
        <>
          <h1 className="text-title-2 font-semibold text-ink">{t('billing.result.missing')}</h1>
          <Link to="/billing" className={buttonClass('secondary')}>
            {t('billing.result.backToBilling')}
          </Link>
        </>
      )}
    </Card>
  );
}
