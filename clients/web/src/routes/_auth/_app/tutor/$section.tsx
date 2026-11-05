import { createFileRoute, notFound } from '@tanstack/react-router';
import { EmptyState } from '@/components/ui/empty-state';

const SECTIONS = ['students', 'plans', 'tasks', 'materials'];

/** Tutor workspace screens arrive in phase 10; the navigation is in place (AC-05.03.3). */
export const Route = createFileRoute('/_auth/_app/tutor/$section')({
  beforeLoad: ({ params, context }) => {
    if (!SECTIONS.includes(params.section) || !context.session.user.roles.includes('TUTOR')) {
      throw notFound();
    }
  },
  component: () => (
    <EmptyState title="Invite your first student" text="Share a link or a code. Students get Pro while enrolled." />
  ),
});
