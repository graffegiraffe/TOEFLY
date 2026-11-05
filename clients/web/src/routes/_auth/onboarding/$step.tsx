import { Role } from '@/api/generated';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { consentsQuery, onboardingQuery } from '@/api/queries';
import { OnboardingWizard } from '@/components/onboarding/onboarding-wizard';
import { isOnboardingStep, resumeStep } from '@/lib/onboarding';

export const Route = createFileRoute('/_auth/onboarding/$step')({
  beforeLoad: async ({ context, params }) => {
    if (!context.profile.roles.includes(Role.Student) || context.profile.onboardingStatus === 'COMPLETED') {
      throw redirect({ to: '/' });
    }
    const state = await context.queryClient.ensureQueryData(onboardingQuery);
    if (!isOnboardingStep(params.step)) {
      throw redirect({ to: '/onboarding/$step', params: { step: resumeStep(state) } });
    }
    return { step: params.step };
  },
  loader: ({ context }) => context.queryClient.ensureQueryData(consentsQuery),
  component: OnboardingRoute,
});

function OnboardingRoute() {
  const { step } = Route.useRouteContext();
  return <OnboardingWizard step={step} />;
}
