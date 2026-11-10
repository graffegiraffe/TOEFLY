import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { HeartbeatRequestStateEnum, type Attempt, type AttemptReview } from '@/api/generated/practice';
import { answerTransport, attemptVersion, newKey, sendHeartbeat, submitAttempt } from '@/api/practice';
import { ActiveTimeTracker } from '@/lib/active-time';
import { AnswerSync } from '@/lib/practice/answer-sync';
import { fromApi, isAnswered, type Answer } from '@/lib/practice/answers';

export const HEARTBEAT_MS = 15_000;

export type SaveState = 'idle' | 'saving' | 'saved' | 'offline';

/**
 * Everything a running practice attempt needs: the answers with autosave (US-06.03), the active time with
 * heartbeats every 15 s (US-16.01), the pause (AC-06.01.3) and the submission.
 */
export function useAttemptSession(attempt: Attempt) {
  const attemptId = attempt.attemptId;
  const sync = useMemo(
    () => new AnswerSync(attemptId, attemptVersion(attempt), answerTransport(attemptId)),
    // one sync per attempt; later versions come from the server responses
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [attemptId],
  );
  const tracker = useMemo(() => new ActiveTimeTracker(), []);
  const [answers, setAnswers] = useState<Record<string, Answer | null>>(() => {
    const initial: Record<string, Answer | null> = {};
    attempt.items.forEach((item) => {
      initial[item.itemId] = fromApi(item.answer);
    });
    return { ...initial, ...sync.unsynced };
  });
  const [paused, setPaused] = useState(attempt.paused);
  const [counted, setCounted] = useState(0);
  const [idle, setIdle] = useState(false);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const replays = useRef(0);
  const sequence = useRef(0);
  // after the submission the attempt takes no more heartbeats (the server answers 409)
  const submitted = useRef(false);
  const currentItem = useRef<string | null>(attempt.items[0]?.itemId ?? null);

  const beat = useCallback(
    (state: HeartbeatRequestStateEnum) => {
      if (submitted.current) return;
      sequence.current = Math.max(sequence.current + 1, Date.now());
      const delta = tracker.take();
      void sendHeartbeat(attemptId, {
        sequence: sequence.current,
        state,
        activeSecondsDelta: delta,
        currentItemId: currentItem.current,
        audioReplays: replays.current,
      });
    },
    [attemptId, tracker],
  );

  // the server knows the attempt was paused: the tracker starts paused too
  useEffect(() => {
    tracker.setPaused(attempt.paused);
  }, [attempt.paused, tracker]);

  useEffect(() => {
    const onInput = () => tracker.input();
    const onVisibility = () => {
      const visible = document.visibilityState !== 'hidden';
      tracker.setVisible(visible);
      if (!visible) beat(HeartbeatRequestStateEnum.Active);
    };
    const events = ['keydown', 'pointerdown', 'touchstart', 'wheel', 'scroll'] as const;
    events.forEach((name) => document.addEventListener(name, onInput, { passive: true, capture: true }));
    document.addEventListener('visibilitychange', onVisibility);
    tracker.setVisible(document.visibilityState !== 'hidden');
    const second = setInterval(() => {
      tracker.tick();
      setCounted(tracker.countedSeconds);
      setIdle(tracker.idle);
    }, 1000);
    const heartbeat = setInterval(() => beat(HeartbeatRequestStateEnum.Active), HEARTBEAT_MS);
    return () => {
      events.forEach((name) => document.removeEventListener(name, onInput, { capture: true }));
      document.removeEventListener('visibilitychange', onVisibility);
      clearInterval(second);
      clearInterval(heartbeat);
      beat(HeartbeatRequestStateEnum.Active);
    };
  }, [beat, tracker]);

  useEffect(
    () =>
      sync.onChange(() => {
        setSaveState(sync.hasPending ? 'saving' : 'saved');
      }),
    [sync],
  );

  useEffect(() => () => sync.dispose(), [sync]);

  const setAnswer = useCallback(
    (itemId: string, answer: Answer | null) => {
      setAnswers((current) => ({ ...current, [itemId]: answer }));
      sync.change(itemId, answer);
      tracker.input();
    },
    [sync, tracker],
  );

  // a failed background save (offline) is retried when the network is back
  useEffect(() => {
    const retry = () => {
      if (!sync.hasPending) return;
      sync.flush().then(
        () => setSaveState('saved'),
        () => setSaveState('offline'),
      );
    };
    window.addEventListener('online', retry);
    const unsubscribe = sync.onChange(() => {
      if (sync.hasPending && !navigator.onLine) setSaveState('offline');
    });
    return () => {
      window.removeEventListener('online', retry);
      unsubscribe();
    };
  }, [sync]);

  const pause = useCallback(() => {
    tracker.setPaused(true);
    setPaused(true);
    beat(HeartbeatRequestStateEnum.Paused);
  }, [beat, tracker]);

  const resume = useCallback(() => {
    tracker.setPaused(false);
    tracker.input();
    setPaused(false);
    setIdle(false);
    beat(HeartbeatRequestStateEnum.Resumed);
  }, [beat, tracker]);

  const wake = useCallback(() => {
    tracker.input();
    setIdle(false);
  }, [tracker]);

  const submitKey = useRef(newKey());

  /** Sends what is left and checks the attempt; throws OfflineError while there is no network. */
  const submit = useCallback(async (): Promise<AttemptReview> => {
    sync.dispose();
    try {
      await sync.flush();
    } catch {
      // what could not be saved goes along with the submission below
    }
    const finalAnswers = Object.entries(sync.unsynced)
      .filter((entry): entry is [string, Answer] => isAnswered(entry[1]))
      .map(([itemId, answer]) => ({ itemId, answer }));
    beat(HeartbeatRequestStateEnum.Active);
    const review = await submitAttempt(attemptId, submitKey.current, finalAnswers);
    submitted.current = true;
    sync.forget();
    return review;
  }, [attemptId, beat, sync]);

  return {
    answers,
    setAnswer,
    paused,
    pause,
    resume,
    idle,
    wake,
    counted,
    saveState,
    submit,
    setMediaActive: useCallback((active: boolean) => tracker.setMediaActive(active), [tracker]),
    countReplay: useCallback(() => {
      replays.current += 1;
    }, []),
    setCurrentItem: useCallback((itemId: string) => {
      currentItem.current = itemId;
    }, []),
    flush: useCallback(() => sync.flush(), [sync]),
  };
}

export type AttemptSession = ReturnType<typeof useAttemptSession>;
