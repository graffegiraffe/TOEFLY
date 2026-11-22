import { createFileRoute, redirect } from '@tanstack/react-router';

/** The Billing tab of Settings leads to the billing page of docs/07-ux-ui.md §5.20. */
export const Route = createFileRoute('/_auth/_app/settings/billing')({
  beforeLoad: () => {
    throw redirect({ to: '/billing' });
  },
});
