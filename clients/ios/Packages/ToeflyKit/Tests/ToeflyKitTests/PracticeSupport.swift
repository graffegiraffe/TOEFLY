import Foundation
import Testing
@testable import ToeflyCore
@testable import ToeflyFeatures

/// A clock the tests move by hand.
final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var current = Date(timeIntervalSince1970: 1_795_000_000)

    var now: Date {
        lock.withLock { current }
    }

    func advance(_ seconds: TimeInterval) {
        lock.withLock { current += seconds }
    }

    var closure: @Sendable () -> Date {
        { [self] in now }
    }
}

/// The fixture practice repository with failures switched on by the test: offline answers and submissions, a stale
/// version once.
final class FlakyPractice: PracticeRepository, @unchecked Sendable {
    let inner: any PracticeRepository
    private let lock = NSLock()
    private var _offline = false
    private var _staleOnce = false
    private var _beats: [Heartbeat] = []
    private var _recorded = 0

    init(_ inner: any PracticeRepository) {
        self.inner = inner
    }

    var offline: Bool {
        get { lock.withLock { _offline } }
        set { lock.withLock { _offline = newValue } }
    }

    var staleOnce: Bool {
        get { lock.withLock { _staleOnce } }
        set { lock.withLock { _staleOnce = newValue } }
    }

    var beats: [Heartbeat] {
        lock.withLock { _beats }
    }

    var recorded: Int {
        lock.withLock { _recorded }
    }

    func openAttempt(itemSetId: UUID, weekTaskId: UUID?) async throws(APIError) -> PracticeAttempt {
        try await inner.openAttempt(itemSetId: itemSetId, weekTaskId: weekTaskId)
    }

    func attempt(id: UUID) async throws(APIError) -> PracticeAttempt {
        try await inner.attempt(id: id)
    }

    func recordAnswer(_ answer: Answer, itemId: UUID, attemptId: UUID, version: Int) async throws(APIError) -> Int {
        if offline {
            throw .offline
        }
        let stale = lock.withLock {
            defer { _staleOnce = false }
            return _staleOnce
        }
        if stale {
            throw .server(status: 412, title: "Precondition failed", traceId: nil)
        }
        lock.withLock { _recorded += 1 }
        return try await inner.recordAnswer(answer, itemId: itemId, attemptId: attemptId, version: version)
    }

    func clearAnswer(itemId: UUID, attemptId: UUID, version: Int) async throws(APIError) -> Int {
        if offline {
            throw .offline
        }
        return try await inner.clearAnswer(itemId: itemId, attemptId: attemptId, version: version)
    }

    func heartbeat(_ beat: Heartbeat, attemptId: UUID) async throws(APIError) {
        lock.withLock { _beats.append(beat) }
        if offline {
            throw .offline
        }
        try await inner.heartbeat(beat, attemptId: attemptId)
    }

    func submit(attemptId: UUID, idempotencyKey: String, finalAnswers: [UUID: Answer]) async throws(APIError)
        -> AttemptReview {
        if offline {
            throw .offline
        }
        return try await inner.submit(attemptId: attemptId, idempotencyKey: idempotencyKey, finalAnswers: finalAnswers)
    }

    func review(attemptId: UUID) async throws(APIError) -> AttemptReview {
        try await inner.review(attemptId: attemptId)
    }
}

/// Week 3 of the fixture plan and a player for one of its tasks.
@MainActor
struct PlayerHarness {
    let fixture: FixtureRepositories
    let practice: FlakyPractice
    let clock = TestClock()
    let store = InMemoryPendingAnswerStore()

    init(_ scenario: FixtureScenario = .withPlan) {
        fixture = fixtureRepositories(scenario)
        practice = FlakyPractice(fixture.practice)
    }

    func task(_ title: String) async throws -> WeekTask {
        let week = try await fixture.planning.week(id: weekThree)
        return try #require(week.tasks.first { $0.title == title })
    }

    func player(_ title: String) async throws -> TaskPlayerViewModel {
        let task = try await task(title)
        let model = try TaskPlayerViewModel(
            title: task.title,
            itemSetId: #require(task.itemSetId),
            weekTaskId: task.id,
            repository: practice,
            store: store,
            now: clock.closure,
            sleep: { _ in },
            makeKey: { "submit-key" },
        )
        await model.load()
        return model
    }

    /// One second of the screen's timer loop.
    func seconds(_ count: Int, of model: TaskPlayerViewModel) async {
        for _ in 0 ..< count {
            clock.advance(1)
            await model.tick()
        }
    }
}

let weekThree = UUID(uuid: (
    0x01,
    0x93,
    0xA5,
    0xB0,
    0x7C,
    0x1E,
    0x7A,
    0x10,
    0x9D,
    0x2E,
    0x6F,
    0x4B,
    0x8C,
    0x1A,
    0x30,
    0x03,
))
