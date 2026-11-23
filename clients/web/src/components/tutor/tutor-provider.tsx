import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { entitlementsQuery } from '@/api/billing';
import type { ScreenContext } from '@/api/tutor';
import { openPaywall } from '@/lib/paywall';
import { AI_TUTOR_CHAT, TutorContext, type TutorRequest, type TutorValue } from './use-tutor';

/** Holds the state of Toefly Tutor for the app frame: the panel, the screen context, exam flows, the thread. */
export function TutorProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [request, setRequest] = useState<TutorRequest>({ nonce: 0, context: null, fresh: false });
  const [screenContext, setScreenContext] = useState<ScreenContext | null>(null);
  const [hiddenCount, setHidden] = useState(0);
  const [threadId, setThreadId] = useState<string | null>(null);

  const openTutor = useCallback(
    (context?: ScreenContext | null) => {
      void (async () => {
        // AC-17.01.5: without the right the Paywall opens instead of the panel. If billing cannot answer, the
        // panel opens and the service refuses with 403, which opens the Paywall as well.
        try {
          const rights = await client.fetchQuery({ ...entitlementsQuery, staleTime: 60_000 });
          if (!(rights.entitlements as string[]).includes(AI_TUTOR_CHAT)) {
            openPaywall(AI_TUTOR_CHAT);
            return;
          }
        } catch {
          // unknown rights: let the service decide
        }
        const fresh = context !== undefined && context !== null;
        if (fresh) setThreadId(null);
        setRequest((previous) => ({
          nonce: previous.nonce + 1,
          context: context === undefined ? screenContext : context,
          fresh,
        }));
        setOpen(true);
      })();
    },
    [client, screenContext],
  );

  // An exam flow closes the panel; it does not come back by itself afterwards.
  const setHiddenCount = useCallback((change: number) => {
    if (change > 0) setOpen(false);
    setHidden((count) => Math.max(0, count + change));
  }, []);
  const hidden = hiddenCount > 0;

  const value = useMemo<TutorValue>(
    () => ({
      open: open && !hidden,
      request,
      screenContext,
      hidden,
      threadId,
      setThreadId,
      openTutor,
      closeTutor: () => setOpen(false),
      setScreenContext,
      setHiddenCount,
    }),
    [open, hidden, request, screenContext, threadId, openTutor, setHiddenCount],
  );
  return <TutorContext.Provider value={value}>{children}</TutorContext.Provider>;
}
