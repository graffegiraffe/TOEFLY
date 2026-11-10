import { describe, expect, it } from 'vitest';
import { ActiveTimeTracker, type Clock } from './active-time';

class FakeClock implements Clock {
  value = 0;
  now() {
    return this.value;
  }
  advance(seconds: number) {
    this.value += seconds * 1000;
  }
}

function setup() {
  const clock = new FakeClock();
  return { clock, tracker: new ActiveTimeTracker(clock) };
}

describe('US-16.01 active time', () => {
  it('AC-16.01.1: time between inputs counts', () => {
    const { clock, tracker } = setup();
    clock.advance(10);
    tracker.input();
    clock.advance(5);
    tracker.input();
    expect(tracker.take()).toBe(15);
    expect(tracker.take()).toBe(0);
  });

  it('AC-16.01.2: a hidden tab does not count', () => {
    const { clock, tracker } = setup();
    clock.advance(10);
    tracker.input();
    tracker.setVisible(false);
    clock.advance(60);
    tracker.setVisible(true);
    clock.advance(5);
    tracker.input();
    expect(tracker.take()).toBe(15);
  });

  it('AC-16.01.3: two minutes without input are not credited and the tracker is idle', () => {
    const { clock, tracker } = setup();
    clock.advance(20);
    tracker.input();
    clock.advance(150);
    tracker.tick();
    expect(tracker.idle).toBe(true);
    tracker.input();
    expect(tracker.idle).toBe(false);
    expect(tracker.take()).toBe(20);
    clock.advance(10);
    tracker.input();
    expect(tracker.take()).toBe(10);
  });

  it('AC-16.01.3: playing audio counts without input', () => {
    const { clock, tracker } = setup();
    tracker.setMediaActive(true);
    clock.advance(200);
    tracker.tick();
    tracker.setMediaActive(false);
    expect(tracker.idle).toBe(false);
    expect(tracker.take()).toBe(30);
    expect(tracker.take()).toBe(30);
  });

  it('AC-16.01.4: a Practice pause does not count', () => {
    const { clock, tracker } = setup();
    clock.advance(10);
    tracker.input();
    tracker.setPaused(true);
    clock.advance(100);
    tracker.setPaused(false);
    tracker.input();
    clock.advance(3);
    tracker.input();
    expect(tracker.take()).toBe(13);
  });

  it('AC-06.01.2: the timer shows what is counted, and drops the idle stretch', () => {
    const { clock, tracker } = setup();
    clock.advance(20);
    tracker.tick();
    expect(tracker.countedSeconds).toBe(20);
    tracker.input();
    expect(tracker.take()).toBe(20);
    expect(tracker.countedSeconds).toBe(20);
    clock.advance(130);
    tracker.tick();
    expect(tracker.idle).toBe(true);
    expect(tracker.countedSeconds).toBe(20);
  });
});
