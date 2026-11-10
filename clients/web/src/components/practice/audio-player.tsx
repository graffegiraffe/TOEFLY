import { AlertTriangle, Pause, Play, RotateCcw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { formatClock } from '@/lib/timer';

interface AudioPlayerProps {
  src: string | undefined;
  autoPlay?: boolean;
  /** The first listening ended: the questions may appear. */
  onFinished: () => void;
  onReplay?: () => void;
  /** Playing audio counts as activity (AC-16.01.3). */
  onPlayingChange?: (playing: boolean) => void;
  /** Practice opens replay, seeking and speed after the first listening (docs/07-ux-ui.md §3.7). */
  replayable?: boolean;
}

const SPEEDS = [0.75, 1, 1.25];

export function AudioPlayer({
  src,
  autoPlay,
  onFinished,
  onReplay,
  onPlayingChange,
  replayable = true,
}: AudioPlayerProps) {
  const { t } = useTranslation();
  const audio = useRef<HTMLAudioElement>(null);
  const [state, setState] = useState<'idle' | 'playing' | 'paused' | 'finished' | 'error'>(src ? 'idle' : 'error');
  const [heardOnce, setHeardOnce] = useState(false);
  const [time, setTime] = useState({ current: 0, duration: 0 });
  const [speed, setSpeed] = useState(1);

  useEffect(() => {
    if (!src || !autoPlay) return;
    audio.current?.play().catch(() => setState((current) => (current === 'error' ? current : 'idle')));
  }, [src, autoPlay]);

  useEffect(() => () => onPlayingChange?.(false), [onPlayingChange]);

  const play = () => {
    const element = audio.current;
    if (!element) return;
    if (state === 'finished') {
      element.currentTime = 0;
      onReplay?.();
    }
    void element.play().catch(() => setState('error'));
  };

  const retry = () => {
    setState('idle');
    audio.current?.load();
    void audio.current?.play().catch(() => setState('error'));
  };

  if (state === 'error') {
    return (
      <div
        role="alert"
        className="flex flex-col items-start gap-2 rounded-xl border border-border bg-warning-soft p-4 text-body text-ink"
      >
        <p className="flex items-center gap-2 font-semibold">
          <AlertTriangle className="size-4 text-warning" aria-hidden="true" />
          {t('practice.listening.unavailable')}
        </p>
        <p className="text-caption text-ink-muted">{t('practice.listening.unavailableHint')}</p>
        {src && (
          <Button variant="secondary" onClick={retry}>
            {t('practice.listening.retry')}
          </Button>
        )}
        {src && <audio ref={audio} src={src} preload="auto" onCanPlay={() => setState('idle')} />}
      </div>
    );
  }

  const seekable = replayable && heardOnce;
  const progress = time.duration > 0 ? (time.current / time.duration) * 100 : 0;
  const playing = state === 'playing';

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-surface-task p-3">
      <audio
        ref={audio}
        src={src}
        preload="auto"
        onPlay={() => {
          setState('playing');
          onPlayingChange?.(true);
        }}
        onPause={() => {
          setState((current) => (current === 'finished' ? current : 'paused'));
          onPlayingChange?.(false);
        }}
        onEnded={() => {
          setState('finished');
          onPlayingChange?.(false);
          if (!heardOnce) {
            setHeardOnce(true);
            onFinished();
          }
        }}
        onError={() => {
          setState('error');
          onPlayingChange?.(false);
        }}
        onTimeUpdate={(event) =>
          setTime({ current: event.currentTarget.currentTime, duration: event.currentTarget.duration || 0 })
        }
        onLoadedMetadata={(event) => setTime({ current: 0, duration: event.currentTarget.duration || 0 })}
      />
      {state === 'finished' ? (
        replayable ? (
          <Button variant="secondary" onClick={play}>
            <RotateCcw className="size-4" aria-hidden="true" />
            {t('practice.listening.replay')}
          </Button>
        ) : (
          <span className="text-body text-ink-muted">{t('practice.listening.finished')}</span>
        )
      ) : (
        <button
          type="button"
          onClick={playing ? () => audio.current?.pause() : play}
          disabled={playing && !replayable}
          aria-label={playing ? t('practice.listening.pauseAudio') : t('practice.listening.play')}
          className="grid size-11 place-items-center rounded-full bg-primary text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring disabled:opacity-50"
        >
          {playing ? <Pause className="size-5" aria-hidden="true" /> : <Play className="size-5" aria-hidden="true" />}
        </button>
      )}
      <input
        type="range"
        min={0}
        max={time.duration || 0}
        step={0.1}
        value={time.current}
        disabled={!seekable}
        onChange={(event) => {
          if (audio.current) audio.current.currentTime = Number(event.target.value);
        }}
        aria-label={playing ? t('practice.listening.playing') : t('practice.listening.finished')}
        className="h-1.5 min-w-32 flex-1 accent-[var(--color-primary)]"
        style={{ backgroundSize: `${progress}% 100%` }}
      />
      <span className="font-mono text-caption tabular-nums text-ink-muted">
        {formatClock(time.current)} / {formatClock(time.duration)}
      </span>
      {seekable && (
        <label className="flex items-center gap-1 text-caption text-ink-muted">
          {t('practice.listening.speed')}
          <select
            value={speed}
            onChange={(event) => {
              const value = Number(event.target.value);
              setSpeed(value);
              if (audio.current) audio.current.playbackRate = value;
            }}
            className="rounded-md border border-border bg-surface px-1 py-0.5 text-ink"
          >
            {SPEEDS.map((value) => (
              <option key={value} value={value}>
                {value}×
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}
