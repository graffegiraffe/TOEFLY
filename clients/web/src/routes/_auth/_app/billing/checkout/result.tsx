import { createFileRoute } from '@tanstack/react-router';
import { useMemo } from 'react';
import { CheckoutResult } from '@/components/billing/checkout-result';
import { checkoutSessionId } from '@/lib/billing';

/** Return page of the hosted checkout (docs/07-ux-ui.md §5.20); the provider may append `?session=<id>`. */
export const Route = createFileRoute('/_auth/_app/billing/checkout/result')({
  component: CheckoutResultPage,
});

function CheckoutResultPage() {
  const sessionId = useMemo(() => checkoutSessionId(window.location.search), []);
  return (
    <div className="flex flex-col gap-6 pt-6">
      <CheckoutResult sessionId={sessionId} />
    </div>
  );
}
