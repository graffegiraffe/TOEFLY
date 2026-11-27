import { createContext, useContext, useEffect } from 'react';
import type { ScreenContext } from '@/api/tutor';

/**
 * Where Toefly Tutor is opened from (docs/07-ux-ui.md §4.2, §5.17): screens publish their context (AC-17.01.2),
 * exam flows hide the button (AC-17.05.1), "Ask tutor" opens the panel with a context of its own.
 */
export interface TutorRequest {
  /** Bumped on every request, so the panel resets even for the same context. */
  nonce: number;
  /** Context of the chip; null for none. */
  context: ScreenContext | null;
  /** "Ask tutor": a new chat about this context instead of the last thread. */
  fresh: boolean;
}

export interface TutorValue {
  open: boolean;
  request: TutorRequest;
  screenContext: ScreenContext | null;
  /** True while an exam flow is on screen. */
  hidden: boolean;
  /** The thread shown in the panel; null for a new chat. */
  threadId: string | null;
  setThreadId: (threadId: string | null) => void;
  /** Opens the panel: without an argument with the context of the screen, with one as a new chat about it. */
  openTutor: (context?: ScreenContext | null) => void;
  closeTutor: () => void;
  setScreenContext: (context: ScreenContext | null) => void;
  setHiddenCount: (change: number) => void;
}

export const TutorContext = createContext<TutorValue | null>(null);

export const AI_TUTOR_CHAT = 'AI_TUTOR_CHAT';

const NOOP: TutorValue = {
  open: false,
  request: { nonce: 0, context: null, fresh: false },
  screenContext: null,
  hidden: true,
  threadId: null,
  setThreadId: () => undefined,
  openTutor: () => undefined,
  closeTutor: () => undefined,
  setScreenContext: () => undefined,
  setHiddenCount: () => undefined,
};

/** The tutor of the app frame; outside it (tests of single screens) a stub that does nothing. */
export function useTutor(): TutorValue {
  return useContext(TutorContext) ?? NOOP;
}

/** A screen puts its context into the chip while it is shown (AC-17.01.2). */
export function useTutorScreen(context: ScreenContext | null) {
  const { setScreenContext } = useTutor();
  const type = context?.type;
  const ref = context?.ref;
  const itemId = context?.itemId;
  const label = context?.label;
  useEffect(() => {
    if (!type || label === undefined) return;
    setScreenContext({ type, ref, itemId, label });
    return () => setScreenContext(null);
  }, [setScreenContext, type, ref, itemId, label]);
}

/** Exam flows hide the AI Tutor button and close the panel (AC-17.05.1, AC-03.01.7). */
export function useHideTutor(hide: boolean) {
  const { setHiddenCount } = useTutor();
  useEffect(() => {
    if (!hide) return;
    setHiddenCount(1);
    return () => setHiddenCount(-1);
  }, [hide, setHiddenCount]);
}
