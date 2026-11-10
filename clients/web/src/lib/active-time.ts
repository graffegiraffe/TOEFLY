/**
 * Active time of a task (docs/03-domain-model.md §13, US-16.01). Only visible time counts; the time between two
 * inputs counts only when they are less than two minutes apart, so the last two minutes before going idle are never
 * credited; playing audio counts without input. The server gets whole seconds, at most 30 per heartbeat.
 */
export const IDLE_MS = 120_000;
export const MAX_DELTA_SECONDS = 30;

export interface Clock {
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

export class ActiveTimeTracker {
  private visible = true;
  private paused = false;
  private media = false;
  private lastInput: number;
  private lastTick: number;
  /** Credited and not yet sent, in milliseconds. */
  private confirmed = 0;
  /** Counted since the last input; credited by the next input if it comes in time. */
  private tentative = 0;
  /** Already handed out by take(), in milliseconds. */
  private sent = 0;

  constructor(
    private readonly clock: Clock = systemClock,
    private readonly idleMs: number = IDLE_MS,
  ) {
    this.lastInput = clock.now();
    this.lastTick = this.lastInput;
  }

  /** A key press, click, touch or scroll. */
  input(): void {
    this.tick();
    const now = this.clock.now();
    if (now - this.lastInput < this.idleMs) {
      this.confirmed += this.tentative;
    }
    this.tentative = 0;
    this.lastInput = now;
  }

  setVisible(visible: boolean): void {
    this.tick();
    this.visible = visible;
  }

  setPaused(paused: boolean): void {
    this.tick();
    this.paused = paused;
  }

  /** Audio playing or voice recording: activity without input. */
  setMediaActive(active: boolean): void {
    this.tick();
    this.media = active;
  }

  /** Advances the counters to now; call it every second. */
  tick(): void {
    const now = this.clock.now();
    const elapsed = Math.max(0, now - this.lastTick);
    this.lastTick = now;
    if (!this.visible || this.paused) {
      return;
    }
    if (this.media) {
      this.confirmed += elapsed;
      this.lastInput = now;
      return;
    }
    this.tentative += elapsed;
    if (now - this.lastInput >= this.idleMs) {
      this.tentative = 0;
    }
  }

  /** True after two minutes without input or media while visible: the player shows “Are you still here?”. */
  get idle(): boolean {
    return this.visible && !this.paused && !this.media && this.clock.now() - this.lastInput >= this.idleMs;
  }

  /** Whole seconds to send now; the remainder stays for the next heartbeat. */
  take(): number {
    this.tick();
    const seconds = Math.min(MAX_DELTA_SECONDS, Math.floor(this.confirmed / 1000));
    this.confirmed -= seconds * 1000;
    this.sent += seconds * 1000;
    return seconds;
  }

  /** Whole seconds counted so far, including the stretch since the last input: what the timer shows. */
  get countedSeconds(): number {
    return Math.floor((this.sent + this.confirmed + this.tentative) / 1000);
  }
}
