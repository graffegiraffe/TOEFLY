import { createFileRoute } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { loginUrl, registerUrl } from '@/api/session';
import { buttonClass } from '@/components/ui/button';
import { GlassPanel } from '@/components/ui/card';
import { Logo } from '@/components/ui/logo';
import { Mesh } from '@/components/ui/mesh';

const search = z.object({ redirect: z.string().optional() });

export const Route = createFileRoute('/sign-in')({
  validateSearch: search,
  component: SignIn,
});

/** Welcome screen; sign-in and sign-up pages themselves are the Keycloak theme (docs/07-ux-ui.md §5.2). */
function SignIn() {
  const { t } = useTranslation();
  const { redirect } = Route.useSearch();
  return (
    <div className="relative grid min-h-dvh place-items-center bg-canvas px-4">
      <Mesh variant="auth" />
      <GlassPanel className="relative z-[1] flex w-full max-w-[400px] flex-col items-center gap-6 p-8 text-center">
        <Logo className="text-[28px]" />
        <h1 className="text-title-2 font-semibold text-ink">{t('welcome.tagline')}</h1>
        <div className="flex w-full flex-col gap-3">
          <a href={registerUrl(redirect ?? '/onboarding/goal')} className={buttonClass('primary', true)}>
            {t('welcome.signUp')}
          </a>
          <a href={loginUrl(redirect)} className={buttonClass('secondary', true)}>
            {t('welcome.signIn')}
          </a>
        </div>
        <p className="text-caption text-ink-muted">{t('welcome.tutorSignUp')}</p>
      </GlassPanel>
    </div>
  );
}
