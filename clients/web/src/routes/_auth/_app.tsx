import { Role } from '@/api/generated';
import { useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { onboardingQuery } from '@/api/queries';
import { logout } from '@/api/session';
import { AppShell } from '@/components/shell/app-shell';
import { resumeStep } from '@/lib/onboarding';

export const Route = createFileRoute('/_auth/_app')({
  beforeLoad: async ({ context }) => {
    const { profile, queryClient } = context;
    if (profile.roles.includes(Role.Student) && profile.onboardingStatus !== 'COMPLETED') {
      const state = await queryClient.ensureQueryData(onboardingQuery);
      throw redirect({ to: '/onboarding/$step', params: { step: resumeStep(state) } });
    }
  },
  component: AppLayout,
});

function AppLayout() {
  const { session, profile } = Route.useRouteContext();
  const queryClient = useQueryClient();
  const signOut = async () => {
    const target = await logout();
    queryClient.clear();
    window.location.assign(target);
  };
  return (
    <AppShell
      roles={session.user.roles}
      displayName={profile.displayName ?? profile.email}
      onSignOut={() => void signOut()}
    >
      <Outlet />
    </AppShell>
  );
}
