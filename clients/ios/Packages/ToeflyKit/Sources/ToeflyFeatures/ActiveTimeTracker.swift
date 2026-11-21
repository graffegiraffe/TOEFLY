import Foundation

/// Active time of a task (docs/03-domain-model.md §13, US-16.01), the same rules as the web tracker. Only time in
/// the foreground counts (AC-16.01.2): `scenePhase` other than `.active` stops it at once. The time between two
/// inputs counts only when they are less than two minutes apart, so the last two minutes before going idle are never
/// credited (AC-16.01.3); playing audio counts without input. The pause stops it too (AC-16.01.4). The server gets
/// whole seconds, at most 30 per heartbeat.
public final class ActiveTimeTracker {
    public static let idleSeconds: TimeInterval = 120
    public static let maxDeltaSeconds = 30

    private let now: () -> Date
    private let idleSeconds: TimeInterval
    private var visible = true
    private var paused = false
    private var media = false
    private var lastInput: Date
    private var lastTick: Date
    /// Credited and not sent yet.
    private var confirmed: TimeInterval = 0
    /// Counted since the last input; credited by the next input if it comes in time.
    private var tentative: TimeInterval = 0
    /// Already handed out by `take()`.
    private var sent: TimeInterval = 0

    public init(now: @escaping () -> Date = Date.init, idleSeconds: TimeInterval = ActiveTimeTracker.idleSeconds) {
        self.now = now
        self.idleSeconds = idleSeconds
        lastInput = now()
        lastTick = lastInput
    }

    /// A tap, a key, a scroll.
    public func input() {
        tick()
        let current = now()
        if current.timeIntervalSince(lastInput) < idleSeconds {
            confirmed += tentative
        }
        tentative = 0
        lastInput = current
    }

    /// The app went to the background (false) or came back (true).
    public func setVisible(_ visible: Bool) {
        tick()
        self.visible = visible
    }

    public func setPaused(_ paused: Bool) {
        tick()
        self.paused = paused
    }

    /// Audio playing: activity without input.
    public func setMediaActive(_ active: Bool) {
        tick()
        media = active
    }

    /// Advances the counters to now; called every second.
    public func tick() {
        let current = now()
        let elapsed = max(0, current.timeIntervalSince(lastTick))
        lastTick = current
        guard visible, !paused else { return }
        if media {
            confirmed += elapsed
            lastInput = current
            return
        }
        tentative += elapsed
        if current.timeIntervalSince(lastInput) >= idleSeconds {
            tentative = 0
        }
    }

    /// Two minutes without input or audio in the foreground: "Paused — are you still here?".
    public var isIdle: Bool {
        visible && !paused && !media && now().timeIntervalSince(lastInput) >= idleSeconds
    }

    public var isCounting: Bool {
        visible && !paused && !isIdle
    }

    /// Whole seconds to send now; the remainder stays for the next heartbeat.
    public func take() -> Int {
        tick()
        let seconds = min(Self.maxDeltaSeconds, Int(confirmed.rounded(.down)))
        confirmed -= TimeInterval(seconds)
        sent += TimeInterval(seconds)
        return seconds
    }

    /// Whole seconds counted so far, including the stretch since the last input: what the timer shows.
    public var countedSeconds: Int {
        Int((sent + confirmed + tentative).rounded(.down))
    }
}
