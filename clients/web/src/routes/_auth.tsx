import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { profileQuery, sessionQuery } from '@/api/queries';
import { UnauthenticatedError } from '@/api/session';

/** Everything below needs a web session; the first profile request creates the profile (AC-01.01.5). */
export const Route = createFileRoute('/_auth')({
  beforeLoad: async ({ context, location }) => {
    try {
      const session = await context.queryClient.ensureQueryData(sessionQuery);
      const profile = await context.queryClient.ensureQueryData(profileQuery);
      return { session, profile };
    } catch (error) {
      if (error instanceof UnauthenticatedError) {
        throw redirect({ to: '/sign-in', search: { redirect: location.href } });
      }
      throw error;
    }
  },
  component: Outlet,
});
