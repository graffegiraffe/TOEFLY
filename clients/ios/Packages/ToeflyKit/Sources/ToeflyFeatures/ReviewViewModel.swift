import Foundation
import Observation
import ToeflyCore

/// Review of a closed task (US-11.01, docs/07-ux-ui.md §5.10): the score, the active time against the norm, each
/// question with the learner's answer, the right one, "Why this is correct" and "Why not" for every wrong option. The
/// explanations are stored with the items, so nothing here calls a model (AC-11.01.2).
@MainActor
@Observable
public final class ReviewViewModel {
    public enum LoadState: Equatable {
        case loading
        case loaded
        case failed(APIError)
    }

    public enum OptionState: Equatable, Sendable {
        case neutral
        /// The right answer (chosen or not).
        case correct
        /// The learner's wrong choice.
        case wrongChoice
    }

    public struct OptionRow: Equatable, Identifiable, Sendable {
        public var id: String {
            key
        }

        public let key: String
        public let text: String
        public let state: OptionState
        public let chosen: Bool
        public let whyNot: String?
    }

    /// One gap of Complete the Words: the start of the word, what the learner typed and the right letters.
    public struct GapRow: Equatable, Identifiable, Sendable {
        public var id: Int {
            number
        }

        public let number: Int
        public let prefix: String
        public let typed: String
        public let expected: String
        public let correct: Bool
    }

    public struct QuestionRow: Equatable, Identifiable, Sendable {
        public let id: UUID
        public let number: Int
        public let correct: Bool
        public let prompt: String?
        public let options: [OptionRow]
        /// Build a Sentence: the assembled and the right sentence.
        public let yourSentence: String?
        public let rightSentence: String?
        public let explanation: Explanation?
    }

    public let attemptId: UUID
    public private(set) var loadState: LoadState = .loading
    public private(set) var review: AttemptReview?
    public private(set) var attempt: PracticeAttempt?

    private let repository: any PracticeRepository

    public init(attemptId: UUID, review: AttemptReview? = nil, repository: any PracticeRepository) {
        self.attemptId = attemptId
        self.review = review
        self.repository = repository
    }

    public func load() async {
        if loadState != .loaded {
            loadState = .loading
        }
        do {
            // The attempt brings the questions (and the transcript, now that it is scored).
            async let attempt = repository.attempt(id: attemptId)
            if review == nil {
                review = try await repository.review(attemptId: attemptId)
            }
            self.attempt = try await attempt
            loadState = .loaded
        } catch {
            loadState = .failed(APIError.from(error))
        }
    }

    public var title: String {
        attempt?.taskType?.label ?? review?.taskType?.label ?? "Review"
    }

    public var scoreText: String {
        guard let review else { return "" }
        return "\(review.correct) of \(review.total) correct"
    }

    public var scoreFraction: Double {
        guard let review, review.total > 0 else { return 0 }
        return Double(review.correct) / Double(review.total)
    }

    /// "3:12 (recommended 3:00)" (AC-11.01.1).
    public var timeText: String {
        guard let review else { return "" }
        return "\(Clock.short(review.activeSeconds)) (recommended \(Clock.short(review.estimatedSeconds)))"
    }

    /// AC-16.02.2: more than 30% off the exam pace.
    public var paceText: String? {
        guard let review,
              let deviation = Clock.paceDeviation(active: review.activeSeconds, norm: review.estimatedSeconds)
        else { return nil }
        return deviation > 0
            ? "You were \(deviation)% slower than the exam pace"
            : "You were \(-deviation)% faster than the exam pace"
    }

    /// "+0.1 to your Reading estimate" when this attempt moved θ of the section.
    public var abilityText: String? {
        guard let change = review?.abilityChange else { return nil }
        let delta = ((change.after - change.before) * 10).rounded() / 10
        guard delta != 0 else { return nil }
        let sign = delta > 0 ? "+" : "−"
        return "\(sign)\(String(format: "%.1f", abs(delta))) to your \(change.section.label) estimate"
    }

    /// "You replayed the audio 2 times" (docs/02 EP-08).
    public var replayText: String? {
        guard let review, review.taskType?.isListening == true else { return nil }
        switch review.audioReplays {
        case 0: return nil
        case 1: return "You replayed the audio once"
        default: return "You replayed the audio \(review.audioReplays) times"
        }
    }

    public var isCloze: Bool {
        attempt?.taskType == .completeTheWords
    }

    public var transcript: [TranscriptLine] {
        attempt?.stimulus.transcript ?? []
    }

    /// The paragraph of Complete the Words with the learner's letters (AC-11.01.4).
    public var gaps: [GapRow] {
        guard let review, let attempt, isCloze else { return [] }
        return attempt.items.enumerated().map { offset, item in
            let result = review.items.first { $0.id == item.id }
            return GapRow(
                number: offset + 1,
                prefix: item.content.prefix ?? "",
                typed: result?.userAnswer?.text ?? "",
                expected: result?.correctAnswer.text ?? "",
                correct: result?.correct ?? false,
            )
        }
    }

    public var paragraph: String {
        attempt?.stimulus.markup ?? ""
    }

    public var questions: [QuestionRow] {
        guard let review, !isCloze else { return [] }
        let items = attempt?.items ?? []
        return review.items.map { result in
            let content = items.first { $0.id == result.id }?.content ?? ItemContent()
            let right = Set(result.correctAnswer.optionIds)
            let chosen = Set(result.userAnswer?.optionIds ?? [])
            let options = (content.options ?? []).map { option in
                OptionRow(
                    key: option.key,
                    text: option.text,
                    state: right
                        .contains(option.key) ? .correct : (chosen.contains(option.key) ? .wrongChoice : .neutral),
                    chosen: chosen.contains(option.key),
                    whyNot: result.distractors[option.key]?.text,
                )
            }
            let sentence = { (ids: [String?]) -> String? in
                guard let template = content.template, !template.isEmpty else { return nil }
                var slot = 0
                return template.map { part -> String in
                    if part.kind == .fixed {
                        return part.text ?? ""
                    }
                    defer { slot += 1 }
                    let id = ids.indices.contains(slot) ? ids[slot] : nil
                    return id.flatMap { id in content.chunks?.first { $0.id == id }?.text } ?? "___"
                }.joined(separator: " ")
            }
            return QuestionRow(
                id: result.id,
                number: result.ordinal,
                correct: result.correct,
                prompt: content.prompt ?? content.contextLine ?? content.insertion?.sentence,
                options: options,
                yourSentence: sentence(result.userAnswer?.fragmentIds ?? []),
                rightSentence: sentence(result.correctAnswer.fragmentIds),
                explanation: result.explanation,
            )
        }
    }
}
