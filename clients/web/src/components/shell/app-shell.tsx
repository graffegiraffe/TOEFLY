import { Sparkles } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Mesh } from '@/components/ui/mesh';
import { Sidebar } from './sidebar';

interface AppShellProps {
  roles: readonly string[];
  displayName: string;
  onSignOut: () => void;
  children: ReactNode;
}

/** The frame of the app: mesh-app background, glass sidebar, content, floating AI tutor button. */
export function AppShell({ roles, displayName, onSignOut, children }: AppShellProps) {
  const { t } = useTranslation();
  return (
    <div className="relative flex min-h-dvh bg-canvas">
      <Mesh variant="app" />
      <Sidebar roles={roles} displayName={displayName} onSignOut={onSignOut} />
      <main className="relative z-[1] min-w-0 flex-1 overflow-auto">
        <div className="mx-auto max-w-[1120px] px-5 pb-24 pt-7 md:pl-[22px] md:pr-9">{children}</div>
      </main>
      {roles.includes('STUDENT') && (
        <button
          type="button"
          className="glass fixed bottom-[22px] right-[22px] z-20 grid size-12 place-items-center rounded-full text-brand-700 dark:text-brand-500"
          aria-label={t('nav.aiTutor')}
          title={t('nav.aiTutor')}
        >
          <Sparkles className="size-5" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
