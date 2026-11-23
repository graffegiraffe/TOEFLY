import type { ReactNode } from 'react';
import { TutorLauncher } from '@/components/tutor/tutor-launcher';
import { TutorPanel } from '@/components/tutor/tutor-panel';
import { TutorProvider } from '@/components/tutor/tutor-provider';
import { Mesh } from '@/components/ui/mesh';
import { Sidebar } from './sidebar';

interface AppShellProps {
  roles: readonly string[];
  displayName: string;
  onSignOut: () => void;
  children: ReactNode;
}

/** The frame of the app: mesh-app background, glass sidebar, content, the AI tutor button and panel. */
export function AppShell({ roles, displayName, onSignOut, children }: AppShellProps) {
  const student = roles.includes('STUDENT');
  return (
    <TutorProvider>
      <div className="relative flex min-h-dvh bg-canvas">
        <Mesh variant="app" />
        <Sidebar roles={roles} displayName={displayName} onSignOut={onSignOut} />
        <main className="relative z-[1] min-w-0 flex-1 overflow-auto">
          <div className="mx-auto max-w-[1120px] px-5 pb-24 pt-7 md:pl-[22px] md:pr-9">{children}</div>
        </main>
        {student && <TutorLauncher />}
        {student && <TutorPanel />}
      </div>
    </TutorProvider>
  );
}
