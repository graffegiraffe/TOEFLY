import Foundation
import ToeflyCore

/// How the player lays out a closed task type (docs/07-ux-ui.md §5.9). Types without a layout open in later
/// sub-phases: Writing and Speaking (12c), exercises and legacy sets (12d).
public enum PlayerLayout: Equatable, Sendable {
    /// `COMPLETE_THE_WORDS`: one paragraph with all the gaps.
    case cloze
    /// `READ_DAILY_LIFE`: the text in the frame of its genre, then one question per screen.
    case dailyLife
    /// `READ_ACADEMIC_PASSAGE`: Passage / Question segments.
    case passage
    /// `LISTEN_CHOOSE_RESPONSE`: one line of audio per item.
    case chooseResponse
    /// The other Listening types: one audio for the set, then the questions.
    case setAudio
    /// `BUILD_A_SENTENCE`: slots and fragments.
    case buildSentence

    public static func of(_ type: TaskType?) -> PlayerLayout? {
        switch type {
        case .completeTheWords: .cloze
        case .readDailyLife: .dailyLife
        case .readAcademicPassage: .passage
        case .listenChooseResponse: .chooseResponse
        case .listenConversation, .listenAnnouncement, .listenAcademicTalk: .setAudio
        case .buildASentence: .buildSentence
        default: nil
        }
    }

    /// All the items on one screen (no question dots, Submit right away).
    public var isSinglePage: Bool {
        self == .cloze
    }

    public var hasAudio: Bool {
        self == .chooseResponse || self == .setAudio
    }
}

/// Complete the Words (US-07.01): the paragraph with each gap as `prefix{_}`; gaps follow the items in ordinal order.
public enum Cloze {
    public enum Segment: Equatable, Sendable {
        case text(String)
        case gap(index: Int, prefix: String)
    }

    public static func parse(_ markup: String) -> [Segment] {
        var segments: [Segment] = []
        var last = markup.startIndex
        var index = 0
        let pattern = /([\p{L}'\-]*)\{[^}]*\}/
        for match in markup.matches(of: pattern) {
            if match.range.lowerBound > last {
                segments.append(.text(String(markup[last ..< match.range.lowerBound])))
            }
            segments.append(.gap(index: index, prefix: String(match.output.1)))
            index += 1
            last = match.range.upperBound
        }
        if last < markup.endIndex {
            segments.append(.text(String(markup[last...])))
        }
        return segments
    }

    /// AC-07.01.3: a cell takes only Latin letters, an apostrophe or a hyphen, up to the number of cells.
    public static func sanitize(_ text: String, length: Int) -> String {
        String(text.filter { $0.isASCII && ($0.isLetter || $0 == "'" || $0 == "-") }.prefix(max(length, 0)))
    }
}

/// Reading passages (US-07.03): sentences for the insert markers and the highlighted word.
public enum Passage {
    public enum Piece: Equatable, Sendable {
        case text(String, highlighted: Bool)
        case marker(String)
    }

    public static func sentences(_ paragraph: String) -> [String] {
        let parts = paragraph.matches(of: /[^.!?]+(?:[.!?]+["')\]]*|$)\s*/).map { String($0.output) }
        let trimmed = parts.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return trimmed.isEmpty ? [paragraph] : trimmed
    }

    /// Pieces of one paragraph: its text, the highlighted range and the markers between sentences. Markers past the
    /// last sentence go to its end, so a slightly different split never hides a marker.
    public static func pieces(
        of text: String,
        paragraph: Int,
        markers: [InsertMarker] = [],
        highlight: PassageRange? = nil,
    ) -> [Piece] {
        let range = highlight?.paragraph == paragraph ? highlight : nil
        let own = markers.filter { $0.paragraph == paragraph }
        guard !own.isEmpty else { return highlighted(text, range: range) }
        var pieces: [Piece] = []
        let parts = sentences(text)
        for (index, sentence) in parts.enumerated() {
            pieces.append(.text(index > 0 ? " \(sentence)" : sentence, highlighted: false))
            let last = index == parts.count - 1
            for marker in own where marker.afterSentence == index || (last && marker.afterSentence > index) {
                pieces.append(.marker(marker.key))
            }
        }
        return pieces
    }

    private static func highlighted(_ text: String, range: PassageRange?) -> [Piece] {
        guard let range, range.charStart < range.charEnd, range.charEnd <= text.count, range.charStart >= 0 else {
            return [.text(text, highlighted: false)]
        }
        let start = text.index(text.startIndex, offsetBy: range.charStart)
        let end = text.index(text.startIndex, offsetBy: range.charEnd)
        return [
            .text(String(text[..<start]), highlighted: false),
            .text(String(text[start ..< end]), highlighted: true),
            .text(String(text[end...]), highlighted: false),
        ].filter {
            if case let .text(value, _) = $0 {
                !value.isEmpty
            } else {
                true
            }
        }
    }
}

/// Build a Sentence (AC-09.01.2): fragments move between the bank and the slots; a slot holds a fragment id or nil.
public enum Slots {
    public static func from(_ ids: [String?], count: Int) -> [String?] {
        (0 ..< count).map { ids.indices.contains($0) ? ids[$0] : nil }
    }

    public static func bank(_ chunks: [Chunk], slots: [String?]) -> [Chunk] {
        let used = Set(slots.compactMap(\.self))
        return chunks.filter { !used.contains($0.id) }
    }

    /// A fragment already in another slot moves; the one in the target goes where it came from, or to the bank.
    public static func place(_ slots: [String?], chunk: String, at target: Int) -> [String?] {
        guard slots.indices.contains(target) else { return slots }
        var next = slots
        let from = next.firstIndex(of: chunk)
        if from == target {
            return slots
        }
        if let from {
            next[from] = next[target]
        }
        next[target] = chunk
        return next
    }

    /// A tap on a fragment of the bank: the first free slot; nothing when every slot is taken.
    public static func placeInFirstFree(_ slots: [String?], chunk: String) -> [String?] {
        guard let free = slots.firstIndex(where: { $0 == nil }) else { return slots }
        return place(slots, chunk: chunk, at: free)
    }

    public static func remove(_ slots: [String?], chunk: String) -> [String?] {
        slots.map { $0 == chunk ? nil : $0 }
    }
}

/// Practice timer (docs/07-ux-ui.md §3.6): counts down to the ETS norm, then up as overtime.
public enum Clock {
    public static func text(_ seconds: Int) -> String {
        let value = max(0, seconds)
        return String(format: "%02d:%02d", value / 60, value % 60)
    }

    public static func countdown(norm: Int, elapsed: Int) -> (text: String, overtime: Bool) {
        let left = norm - elapsed
        return left >= 0 ? (text(left), false) : ("+\(text(-left))", true)
    }

    /// "3:12": minutes without a leading zero, for the review.
    public static func short(_ seconds: Int) -> String {
        let value = max(0, seconds)
        return String(format: "%d:%02d", value / 60, value % 60)
    }

    /// Pace against the ETS norm (AC-16.02.2): the signed percent when off by more than 30%, else nil.
    public static func paceDeviation(active: Int, norm: Int) -> Int? {
        guard norm > 0, active > 0 else { return nil }
        let ratio = Double(active) / Double(norm)
        return abs(ratio - 1) > 0.3 ? Int(((ratio - 1) * 100).rounded()) : nil
    }
}
