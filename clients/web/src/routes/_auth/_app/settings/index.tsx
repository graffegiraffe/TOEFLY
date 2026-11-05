import { createFileRoute, redirect } from '@tanstack/react-router';

export const Route = createFileRoute('/_auth/_app/settings/')({
  beforeLoad: () => {
    throw redirect({ to: '/settings/profile' });
  },
});
