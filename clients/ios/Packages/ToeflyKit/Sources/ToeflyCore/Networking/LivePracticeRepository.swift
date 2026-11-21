import Foundation
import OpenAPIRuntime
import PracticeAPI

public struct LivePracticeRepository: PracticeRepository {
    private let client: PracticeAPI.Client
    private let timeZone: @Sendable () -> TimeZone
    private let makeKey: @Sendable () -> String

    public init(
        client: PracticeAPI.Client,
        timeZone: @escaping @Sendable () -> TimeZone = { .current },
        makeKey: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
    ) {
        self.client = client
        self.timeZone = timeZone
        self.makeKey = makeKey
    }

    public func openAttempt(itemSetId: UUID, weekTaskId: UUID?) async throws(APIError) -> PracticeAttempt {
        try await call {
            if let weekTaskId {
                let open = try await client.listAttempts(query: .init(
                    weekTaskId: weekTaskId.uuidString.lowercased(),
                    status: [.inProgress],
                    limit: 1,
                ))
                guard case let .ok(page) = open else { throw APIError.unexpected }
                if let existing = try page.body.json.items.first, let id = UUID(uuidString: existing.attemptId) {
                    return try await attemptValue(id: id)
                }
            }
            let output = try await client.startAttempt(
                headers: .init(idempotencyKey: makeKey()),
                body: .json(.init(
                    itemSetId: itemSetId.uuidString.lowercased(),
                    mode: .init(value1: .practice),
                    weekTaskId: weekTaskId?.uuidString.lowercased(),
                    timeZone: timeZone().identifier,
                )),
            )
            guard case let .created(created) = output else { throw APIError.unexpected }
            return try PracticeMapping.attempt(created.body.json)
        }
    }

    public func attempt(id: UUID) async throws(APIError) -> PracticeAttempt {
        try await call { try await attemptValue(id: id) }
    }

    private func attemptValue(id: UUID) async throws -> PracticeAttempt {
        let output = try await client.getAttempt(path: .init(attemptId: id.uuidString.lowercased()))
        guard case let .ok(ok) = output else { throw APIError.unexpected }
        return try PracticeMapping.attempt(ok.body.json)
    }

    public func recordAnswer(
        _ answer: Answer,
        itemId: UUID,
        attemptId: UUID,
        version: Int,
    ) async throws(APIError) -> Int {
        try await call {
            let output = try await client.recordAnswer(
                path: .init(attemptId: attemptId.uuidString.lowercased(), itemId: itemId.uuidString.lowercased()),
                headers: .init(ifMatch: "\"\(version)\""),
                body: .json(.init(answer: PracticeMapping.value(answer))),
            )
            guard case let .ok(ok) = output else { throw APIError.unexpected }
            return try ok.body.json.attemptVersion
        }
    }

    public func clearAnswer(itemId: UUID, attemptId: UUID, version: Int) async throws(APIError) -> Int {
        try await call {
            let output = try await client.clearAnswer(
                path: .init(attemptId: attemptId.uuidString.lowercased(), itemId: itemId.uuidString.lowercased()),
                headers: .init(ifMatch: "\"\(version)\""),
            )
            guard case let .noContent(done) = output else { throw APIError.unexpected }
            return PracticeMapping.version(done.headers.eTag) ?? version + 1
        }
    }

    public func heartbeat(_ beat: Heartbeat, attemptId: UUID) async throws(APIError) {
        try await call {
            let output = try await client.sendAttemptHeartbeat(
                path: .init(attemptId: attemptId.uuidString.lowercased()),
                body: .json(.init(
                    sequence: beat.sequence,
                    state: .init(rawValue: beat.state.rawValue) ?? .active,
                    activeSecondsDelta: min(max(beat.activeSecondsDelta, 0), 30),
                    currentItemId: beat.currentItemId?.uuidString.lowercased(),
                    audioReplays: beat.audioReplays,
                )),
            )
            guard case .noContent = output else { throw APIError.unexpected }
        }
    }

    public func submit(
        attemptId: UUID,
        idempotencyKey: String,
        finalAnswers: [UUID: Answer],
    ) async throws(APIError) -> AttemptReview {
        try await call {
            let pending = finalAnswers.sorted { $0.key.uuidString < $1.key.uuidString }.map {
                PracticeAPI.Components.Schemas.SubmitAttemptRequest.FinalAnswersPayloadPayload(
                    itemId: $0.key.uuidString.lowercased(),
                    answer: PracticeMapping.value($0.value),
                )
            }
            let output = try await client.submitAttempt(
                path: .init(attemptId: attemptId.uuidString.lowercased()),
                headers: .init(idempotencyKey: idempotencyKey),
                body: .json(.init(finalAnswers: pending.isEmpty ? nil : pending, reason: .user)),
            )
            guard case let .ok(ok) = output else { throw APIError.unexpected }
            return try PracticeMapping.review(ok.body.json)
        }
    }

    public func review(attemptId: UUID) async throws(APIError) -> AttemptReview {
        try await call {
            let output = try await client.getAttemptReview(path: .init(attemptId: attemptId.uuidString.lowercased()))
            guard case let .ok(ok) = output else { throw APIError.unexpected }
            return try PracticeMapping.review(ok.body.json)
        }
    }
}

enum PracticeMapping {
    typealias Schemas = PracticeAPI.Components.Schemas

    static func attempt(_ dto: Schemas.Attempt) throws -> PracticeAttempt {
        guard let id = UUID(uuidString: dto.attemptId), let itemSetId = UUID(uuidString: dto.itemSetId)
        else { throw APIError.unexpected }
        return PracticeAttempt(
            id: id,
            itemSetId: itemSetId,
            weekTaskId: dto.weekTaskId.flatMap(UUID.init(uuidString:)),
            taskType: TaskType(rawValue: dto.taskType.rawValue),
            section: dto.section.flatMap { WeekSection(rawValue: $0.rawValue) },
            status: AttemptStatus(rawValue: dto.status.rawValue) ?? .inProgress,
            estimatedSeconds: dto.estimatedSeconds,
            activeSeconds: dto.activeSeconds,
            paused: dto.paused,
            timingMultiplier: dto.timingMultiplier,
            stimulus: dto.stimulus.flatMap { payload($0, as: Stimulus.self) } ?? Stimulus(),
            items: dto.items.compactMap { item in
                UUID(uuidString: item.itemId).map {
                    AttemptItem(
                        id: $0,
                        ordinal: item.ordinal,
                        content: payload(item.content, as: ItemContent.self) ?? ItemContent(),
                        answer: item.answer.map(answer),
                    )
                }
            },
            version: dto.version ?? 0,
        )
    }

    static func review(_ dto: Schemas.AttemptReview) throws -> AttemptReview {
        guard let id = UUID(uuidString: dto.attemptId) else { throw APIError.unexpected }
        return AttemptReview(
            attemptId: id,
            taskType: TaskType(rawValue: dto.taskType.rawValue),
            section: dto.section.flatMap { WeekSection(rawValue: $0.rawValue) },
            correct: dto.score.correct,
            total: dto.score.total,
            unanswered: dto.score.unanswered ?? 0,
            activeSeconds: dto.activeSeconds,
            estimatedSeconds: dto.estimatedSeconds,
            audioReplays: dto.audioReplays ?? 0,
            explanationPending: dto.explanationPending,
            abilityChange: dto.abilityChange.flatMap { change in
                WeekSection(rawValue: change.section.rawValue).map {
                    AbilityChange(section: $0, before: change.thetaBefore, after: change.thetaAfter)
                }
            },
            items: dto.items.compactMap { item in
                UUID(uuidString: item.itemId).map {
                    var distractors: [String: Explanation] = [:]
                    for entry in item.distractorExplanations ?? [] {
                        distractors[entry.optionId] = Explanation(text: entry.text, isAI: entry.source == .ai)
                    }
                    return ItemReview(
                        id: $0,
                        ordinal: item.ordinal,
                        correct: item.correct,
                        userAnswer: item.userAnswer.map(answer),
                        correctAnswer: answer(item.correctAnswer),
                        explanation: item.explanation.map { Explanation(text: $0.text, isAI: $0.source == .ai) },
                        distractors: distractors,
                    )
                }
            },
        )
    }

    static func answer(_ value: Schemas.AnswerValue) -> Answer {
        switch value {
        case let .choice(choice): .choice(choice.optionIds)
        case let .text(text): .text(text.text)
        case let .sequence(sequence): .sequence(sequence.fragmentIds)
        case let .mapping(mapping): .mapping(mapping.pairs.map { AnswerPair(key: $0.key, value: $0.value) })
        }
    }

    static func value(_ answer: Answer) -> Schemas.AnswerValue {
        switch answer {
        case let .choice(ids): .choice(.init(_type: .choice, optionIds: ids))
        case let .text(text): .text(.init(_type: .text, text: text))
        case let .sequence(ids): .sequence(.init(_type: .sequence, fragmentIds: ids))
        case let .mapping(pairs): .mapping(.init(
                _type: .mapping,
                pairs: pairs.map { .init(key: $0.key, value: $0.value) },
            ))
        }
    }

    /// `"7"` → 7.
    static func version(_ eTag: String?) -> Int? {
        eTag.flatMap { Int($0.trimmingCharacters(in: CharacterSet(charactersIn: "\"W/"))) }
    }

    /// The free-form payloads (`additionalProperties: true`) are re-read as the typed views of the players.
    private static func payload<T: Decodable>(_ value: some Encodable, as _: T.Type) -> T? {
        guard let data = try? JSONEncoder().encode(value) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
