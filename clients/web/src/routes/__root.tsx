import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Outlet } from '@tanstack/react-router';
import { EmptyState } from '@/components/ui/empty-state';

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  component: Outlet,
  notFoundComponent: () => (
    <div className="grid min-h-dvh place-items-center bg-canvas">
      <EmptyState
        title="Page not found"
        action={
          <a href="/" className="text-link underline">
            Go home
          </a>
        }
      />
    </div>
  ),
});
