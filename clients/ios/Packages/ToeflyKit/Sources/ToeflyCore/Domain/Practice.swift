import Foundation

/// An answer to an item (practice-service `AnswerValue`, docs/05-api-contracts.md §7.1). Its JSON is the wire format,
/// so the offline queue stores it as it is sent.
public enum Answer: Sendable, Hashable, Codable {
    case choice([String])
    case text(String)
    /// Fragment ids by slot; an empty slot is nil (`BUILD_A_SENTENCE`).
    case sequence([String?])
    case mapping([AnswerPair])

    /// An empty answer is no answer: the player clears it instead of saving it.
    public var isAnswered: Bool {
        switch self {
        case let .choice(ids): !ids.isEmpty
        case let .text(text): !text.trimmingCharacters(in: .whitespaces).isEmpty
        case let .sequence(ids): ids.contains { $0 != nil }
        case let .mapping(pairs): pairs.contains { !$0.value.trimmingCharacters(in: .whitespaces).isEmpty }
        }
    }

    public var optionIds: [String] {
        if case let .choice(ids) = self {
            ids
        } else {
            []
        }
    }

    public var text: String? {
        if case let .text(text) = self {
            text
        } else {
            nil
        }
    }

    public var fragmentIds: [String?] {
        if case let .sequence(ids) = self {
            ids
        } else {
            []
        }
    }

    private enum CodingKeys: String, CodingKey {
        case type, optionIds, text, fragmentIds, pairs
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(String.self, forKey: .type) {
        case "CHOICE": self = try .choice(container.decode([String].self, forKey: .optionIds))
        case "TEXT": self = try .text(container.decode(String.self, forKey: .text))
        case "SEQUENCE": self = try .sequence(container.decode([String?].self, forKey: .fragmentIds))
        case "MAPPING": self = try .mapping(container.decode([AnswerPair].self, forKey: .pairs))
        case let other:
            throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "type \(other)")
        }
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .choice(ids):
            try container.encode("CHOICE", forKey: .type)
            try container.encode(ids, forKey: .optionIds)
        case let .text(text):
            try container.encode("TEXT", forKey: .type)
            try container.encode(text, forKey: .text)
        case let .sequence(ids):
            try container.encode("SEQUENCE", forKey: .type)
            try container.encode(ids, forKey: .fragmentIds)
        case let .mapping(pairs):
            try container.encode("MAPPING", forKey: .type)
            try container.encode(pairs, forKey: .pairs)
        }
    }
}

public struct AnswerPair: Sendable, Hashable, Codable {
    public var key: String
    public var value: String

    public init(key: String, value: String) {
        self.key = key
        self.value = value
    }
}

public enum AttemptStatus: String, Sendable, Equatable {
    case inProgress = "IN_PROGRESS", submitted = "SUBMITTED", scored = "SCORED", abandoned = "ABANDONED"
}

/// Heartbeat state of the time tracker (AC-16.01.1, AC-06.01.3).
public enum HeartbeatState: String, Sendable, Equatable {
    case active = "ACTIVE", paused = "PAUSED", resumed = "RESUMED"
}

// MARK: - Payloads

// What a player reads from `Attempt.stimulus` and `AttemptItem.content` (the payload schemas of
// docs/03-domain-model.md §4 without keys). Every field is optional: the players degrade instead of failing.

public struct ChoiceOption: Sendable, Hashable, Decodable {
    public var key: String
    public var text: String

    public init(key: String, text: String) {
        self.key = key
        self.text = text
    }
}

public struct AudioRef: Sendable, Hashable, Decodable {
    public var url: URL?
    public var durationSeconds: Double?

    public init(url: URL?, durationSeconds: Double? = nil) {
        self.url = url
        self.durationSeconds = durationSeconds
    }
}

/// A range of a passage paragraph: the word of a vocabulary question (AC-07.03.2).
public struct PassageRange: Sendable, Hashable, Decodable {
    public var paragraph: Int
    public var charStart: Int
    public var charEnd: Int

    public init(paragraph: Int, charStart: Int, charEnd: Int) {
        self.paragraph = paragraph
        self.charStart = charStart
        self.charEnd = charEnd
    }
}

/// A position for the inserted sentence: after sentence `afterSentence` (0-based) of a paragraph (AC-07.03.3).
public struct InsertMarker: Sendable, Hashable, Decodable {
    public var key: String
    public var paragraph: Int
    public var afterSentence: Int

    public init(key: String, paragraph: Int, afterSentence: Int) {
        self.key = key
        self.paragraph = paragraph
        self.afterSentence = afterSentence
    }
}

public struct Insertion: Sendable, Hashable, Decodable {
    public var sentence: String
    public var markers: [InsertMarker]

    public init(sentence: String, markers: [InsertMarker]) {
        self.sentence = sentence
        self.markers = markers
    }
}

public struct TemplatePart: Sendable, Hashable, Decodable {
    public enum Kind: String, Sendable, Decodable { case fixed = "FIXED", slot = "SLOT" }
    public var kind: Kind
    public var text: String?
    public var slotId: String?

    public init(kind: Kind, text: String? = nil, slotId: String? = nil) {
        self.kind = kind
        self.text = text
        self.slotId = slotId
    }
}

public struct Chunk: Sendable, Hashable, Decodable, Identifiable {
    public var id: String
    public var text: String

    public init(id: String, text: String) {
        self.id = id
        self.text = text
    }
}

public struct ItemContent: Sendable, Hashable, Decodable {
    public var prompt: String?
    public var introSentence: String?
    public var options: [ChoiceOption]?
    public var selection: String?
    public var subskill: String?
    /// `COMPLETE_THE_WORDS`: the visible start of the word and the number of missing letters.
    public var prefix: String?
    public var missingLetters: Int?
    /// `BUILD_A_SENTENCE`: the line of the other speaker, the slots and the fragments.
    public var contextLine: String?
    public var template: [TemplatePart]?
    public var chunks: [Chunk]?
    /// `READ_ACADEMIC_PASSAGE`: the word of a vocabulary question and the insert-sentence markers.
    public var targetWord: PassageRange?
    public var insertion: Insertion?
    /// `LISTEN_CHOOSE_RESPONSE`: one line per item.
    public var audio: AudioRef?

    public init(
        prompt: String? = nil,
        options: [ChoiceOption]? = nil,
        prefix: String? = nil,
        missingLetters: Int? = nil,
        contextLine: String? = nil,
        template: [TemplatePart]? = nil,
        chunks: [Chunk]? = nil,
        targetWord: PassageRange? = nil,
        insertion: Insertion? = nil,
        audio: AudioRef? = nil,
    ) {
        self.prompt = prompt
        self.options = options
        self.prefix = prefix
        self.missingLetters = missingLetters
        self.contextLine = contextLine
        self.template = template
        self.chunks = chunks
        self.targetWord = targetWord
        self.insertion = insertion
        self.audio = audio
    }

    public var slotCount: Int {
        template?.count(where: { $0.kind == .slot }) ?? 0
    }

    private enum CodingKeys: String, CodingKey {
        case prompt, introSentence, options, selection, subskill, prefix, missingLetters, contextLine, template
        case chunks, targetWord, insertion, audio
    }

    /// Field by field: one malformed field leaves the rest of the item usable.
    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        prompt = try? container.decodeIfPresent(String.self, forKey: .prompt)
        introSentence = try? container.decodeIfPresent(String.self, forKey: .introSentence)
        options = try? container.decodeIfPresent([ChoiceOption].self, forKey: .options)
        selection = try? container.decodeIfPresent(String.self, forKey: .selection)
        subskill = try? container.decodeIfPresent(String.self, forKey: .subskill)
        prefix = try? container.decodeIfPresent(String.self, forKey: .prefix)
        missingLetters = try? container.decodeIfPresent(Int.self, forKey: .missingLetters)
        contextLine = try? container.decodeIfPresent(String.self, forKey: .contextLine)
        template = try? container.decodeIfPresent([TemplatePart].self, forKey: .template)
        chunks = try? container.decodeIfPresent([Chunk].self, forKey: .chunks)
        targetWord = try? container.decodeIfPresent(PassageRange.self, forKey: .targetWord)
        insertion = try? container.decodeIfPresent(Insertion.self, forKey: .insertion)
        audio = try? container.decodeIfPresent(AudioRef.self, forKey: .audio)
    }
}

public struct ChatMessage: Sendable, Hashable, Decodable {
    public var sender: String
    public var time: String?
    public var text: String
}

public struct TranscriptLine: Sendable, Hashable, Decodable {
    public var speaker: String
    public var text: String
}

public struct Speaker: Sendable, Hashable, Decodable {
    public var id: String
    public var role: String
    public var name: String?
}

public struct Stimulus: Sendable, Hashable, Decodable {
    public var title: String?
    /// `COMPLETE_THE_WORDS`: the paragraph with each gap as `prefix{_}`.
    public var markup: String?
    public var paragraphs: [String]?
    public var genre: String?
    public var body: String?
    public var headers: [String: String]?
    public var messages: [ChatMessage]?
    public var contextLine: String?
    public var audio: AudioRef?
    public var speakers: [Speaker]?
    /// Only after the attempt is scored (practice-service adds it to the stimulus).
    public var transcript: [TranscriptLine]?

    public init(
        title: String? = nil,
        markup: String? = nil,
        paragraphs: [String]? = nil,
        genre: String? = nil,
        body: String? = nil,
        contextLine: String? = nil,
        audio: AudioRef? = nil,
    ) {
        self.title = title
        self.markup = markup
        self.paragraphs = paragraphs
        self.genre = genre
        self.body = body
        self.contextLine = contextLine
        self.audio = audio
    }

    private enum CodingKeys: String, CodingKey {
        case title, markup, paragraphs, genre, body, headers, messages, contextLine, audio, speakers, transcript
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = try? container.decodeIfPresent(String.self, forKey: .title)
        markup = try? container.decodeIfPresent(String.self, forKey: .markup)
        paragraphs = try? container.decodeIfPresent([String].self, forKey: .paragraphs)
        genre = try? container.decodeIfPresent(String.self, forKey: .genre)
        body = try? container.decodeIfPresent(String.self, forKey: .body)
        headers = try? container.decodeIfPresent([String: String].self, forKey: .headers)
        messages = try? container.decodeIfPresent([ChatMessage].self, forKey: .messages)
        contextLine = try? container.decodeIfPresent(String.self, forKey: .contextLine)
        audio = try? container.decodeIfPresent(AudioRef.self, forKey: .audio)
        speakers = try? container.decodeIfPresent([Speaker].self, forKey: .speakers)
        // A transcript is a list of lines or one plain string.
        if let lines = try? container.decodeIfPresent([TranscriptLine].self, forKey: .transcript) {
            transcript = lines
        } else if let text = try? container.decodeIfPresent(String.self, forKey: .transcript) {
            transcript = [TranscriptLine(speaker: "", text: text)]
        }
    }
}

// MARK: - Attempts

public struct AttemptItem: Sendable, Hashable, Identifiable {
    public var id: UUID
    public var ordinal: Int
    public var content: ItemContent
    public var answer: Answer?

    public init(id: UUID, ordinal: Int, content: ItemContent, answer: Answer? = nil) {
        self.id = id
        self.ordinal = ordinal
        self.content = content
        self.answer = answer
    }
}

/// A practice attempt (practice-service `Attempt`).
public struct PracticeAttempt: Sendable, Hashable, Identifiable {
    public var id: UUID
    public var itemSetId: UUID
    public var weekTaskId: UUID?
    public var taskType: TaskType?
    public var section: WeekSection?
    public var status: AttemptStatus
    /// ETS norm of the set in seconds.
    public var estimatedSeconds: Int
    public var activeSeconds: Int
    public var paused: Bool
    public var timingMultiplier: Double
    public var stimulus: Stimulus
    public var items: [AttemptItem]
    /// Version of the event stream; `If-Match` of the next answer.
    public var version: Int

    public init(
        id: UUID,
        itemSetId: UUID,
        weekTaskId: UUID? = nil,
        taskType: TaskType?,
        section: WeekSection?,
        status: AttemptStatus = .inProgress,
        estimatedSeconds: Int,
        activeSeconds: Int = 0,
        paused: Bool = false,
        timingMultiplier: Double = 1,
        stimulus: Stimulus = Stimulus(),
        items: [AttemptItem],
        version: Int = 0,
    ) {
        self.id = id
        self.itemSetId = itemSetId
        self.weekTaskId = weekTaskId
        self.taskType = taskType
        self.section = section
        self.status = status
        self.estimatedSeconds = estimatedSeconds
        self.activeSeconds = activeSeconds
        self.paused = paused
        self.timingMultiplier = timingMultiplier
        self.stimulus = stimulus
        self.items = items.sorted { $0.ordinal < $1.ordinal }
        self.version = version
    }
}

public struct Explanation: Sendable, Hashable {
    public var text: String
    /// Generated by the tutor agent rather than stored with the item ("AI explanation", AC-11.01.3).
    public var isAI: Bool

    public init(text: String, isAI: Bool = false) {
        self.text = text
        self.isAI = isAI
    }
}

public struct ItemReview: Sendable, Hashable, Identifiable {
    public var id: UUID
    public var ordinal: Int
    public var correct: Bool
    public var userAnswer: Answer?
    public var correctAnswer: Answer
    public var explanation: Explanation?
    /// "Why not" of each wrong option, by option key (AC-11.01.1).
    public var distractors: [String: Explanation]

    public init(
        id: UUID,
        ordinal: Int,
        correct: Bool,
        userAnswer: Answer?,
        correctAnswer: Answer,
        explanation: Explanation? = nil,
        distractors: [String: Explanation] = [:],
    ) {
        self.id = id
        self.ordinal = ordinal
        self.correct = correct
        self.userAnswer = userAnswer
        self.correctAnswer = correctAnswer
        self.explanation = explanation
        self.distractors = distractors
    }
}

public struct AbilityChange: Sendable, Hashable {
    public var section: WeekSection
    public var before: Double
    public var after: Double

    public init(section: WeekSection, before: Double, after: Double) {
        self.section = section
        self.before = before
        self.after = after
    }
}

/// The review of a closed task (practice-service `AttemptReview`, US-11.01).
public struct AttemptReview: Sendable, Hashable {
    public var attemptId: UUID
    public var taskType: TaskType?
    public var section: WeekSection?
    public var correct: Int
    public var total: Int
    public var unanswered: Int
    public var activeSeconds: Int
    public var estimatedSeconds: Int
    public var audioReplays: Int
    public var explanationPending: Bool
    public var abilityChange: AbilityChange?
    public var items: [ItemReview]

    public init(
        attemptId: UUID,
        taskType: TaskType?,
        section: WeekSection?,
        correct: Int,
        total: Int,
        unanswered: Int = 0,
        activeSeconds: Int,
        estimatedSeconds: Int,
        audioReplays: Int = 0,
        explanationPending: Bool = false,
        abilityChange: AbilityChange? = nil,
        items: [ItemReview],
    ) {
        self.attemptId = attemptId
        self.taskType = taskType
        self.section = section
        self.correct = correct
        self.total = total
        self.unanswered = unanswered
        self.activeSeconds = activeSeconds
        self.estimatedSeconds = estimatedSeconds
        self.audioReplays = audioReplays
        self.explanationPending = explanationPending
        self.abilityChange = abilityChange
        self.items = items.sorted { $0.ordinal < $1.ordinal }
    }
}
