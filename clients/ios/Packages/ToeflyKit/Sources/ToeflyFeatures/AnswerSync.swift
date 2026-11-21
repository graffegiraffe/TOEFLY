import Foundation
import ToeflyCore

/// Autosave of the answers of an attempt (US-06.03). A change waits for the debounce, then goes to the server in the
/// order of the changes with `If-Match` of the attempt version; without a network it stays in a file of the app and
/// goes out on the next save or with the submission. A stale version (412) is read again once.
@MainActor
final class AnswerSync {
    private let attemptId: UUID
    private let repository: any PracticeRepository
    private let store: any PendingAnswerStore
    private(set) var version: Int
    /// Item → answer (nil: cleared), in the order of the first change.
    private(set) var pending: [UUID: Answer?]
    private var order: [UUID]
    /// One flush at a time: a second caller waits for the running one, then sends what is left.
    private var running: Task<APIError?, Never>?

    init(attempt: PracticeAttempt, repository: any PracticeRepository, store: any PendingAnswerStore) {
        attemptId = attempt.id
        self.repository = repository
        self.store = store
        version = attempt.version
        pending = store.load(attemptId: attempt.id)
        order = pending.keys.sorted { $0.uuidString < $1.uuidString }
    }

    var hasPending: Bool {
        !pending.isEmpty
    }

    func change(_ itemId: UUID, to answer: Answer?) {
        if pending[itemId] == nil {
            order.append(itemId)
        }
        pending[itemId] = .some(answer)
        store.save(pending, attemptId: attemptId)
    }

    /// Sends what is pending; throws the first failure and keeps the rest.
    func flush() async throws(APIError) {
        while let running {
            _ = await running.value
            if self.running == running {
                self.running = nil
            }
        }
        let task = Task { @MainActor () -> APIError? in
            do {
                try await self.send()
                return nil
            } catch {
                return APIError.from(error)
            }
        }
        running = task
        let failure = await task.value
        if running == task {
            running = nil
        }
        if let failure {
            throw failure
        }
    }

    private func send() async throws(APIError) {
        var retried = false
        while let itemId = order.first {
            guard let answer = pending[itemId] else {
                order.removeFirst()
                continue
            }
            do {
                if let answer, answer.isAnswered {
                    version = try await repository.recordAnswer(
                        answer,
                        itemId: itemId,
                        attemptId: attemptId,
                        version: version,
                    )
                } else {
                    version = try await repository.clearAnswer(itemId: itemId, attemptId: attemptId, version: version)
                }
                // A newer change made during the request stays pending.
                if pending[itemId] == .some(answer) {
                    pending.removeValue(forKey: itemId)
                    order.removeFirst()
                }
                retried = false
            } catch .server(status: 412, _, _) where !retried {
                retried = true
                version = try await repository.attempt(id: attemptId).version
            } catch {
                store.save(pending, attemptId: attemptId)
                throw error
            }
        }
        store.save(pending, attemptId: attemptId)
    }

    /// Answers that could not be saved go with the submission (AC-06.03.2).
    var unsyncedAnswers: [UUID: Answer] {
        pending.compactMapValues { $0.flatMap { $0.isAnswered ? $0 : nil } }
    }

    func forget() {
        pending = [:]
        order = []
        store.save([:], attemptId: attemptId)
    }
}
