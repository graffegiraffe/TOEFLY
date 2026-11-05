import { createFileRoute, redirect } from '@tanstack/react-router';
import { registerUrl } from '@/api/session';

/** /sign-up goes straight to the Keycloak registration page through the gateway. */
export const Route = createFileRoute('/sign-up')({
  beforeLoad: () => {
    window.location.assign(registerUrl('/onboarding/goal'));
    throw redirect({ to: '/sign-in' });
  },
});
