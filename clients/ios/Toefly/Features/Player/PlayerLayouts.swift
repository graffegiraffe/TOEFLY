import SwiftUI
import ToeflyCore
import ToeflyFeatures

// MARK: - Complete the Words

/// `ClozeInput` (docs/07-ux-ui.md §3.9, US-07.01): the paragraph with an inline field per gap, as wide as its missing
/// letters; filling the last letter moves to the next gap. No autocorrection, capitals or QuickType (AC-07.01.3).
struct ClozePlayer: View {
    let model: TaskPlayerViewModel
    @FocusState private var focused: Int?
    @ScaledMetric(relativeTo: .body) private var letterWidth: CGFloat = 12

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            Text("Fill in the missing letters in the paragraph.")
                .font(.toeflyBodyStrong)
                .foregroundStyle(Color.toefly.inkMuted)
            if let title = model.stimulus.title {
                Text(title).font(.toeflyTitle3).foregroundStyle(Color.toefly.ink).accessibilityAddTraits(.isHeader)
            }
            Color.clear
                .frame(height: 1)
                .accessibilityElement()
                .accessibilityLabel(spokenParagraph)
                .accessibilityIdentifier("cloze-paragraph")
            FlowLayout(spacing: 0, lineSpacing: Space.s2) {
                ForEach(Array(pieces.enumerated()), id: \.offset) { _, piece in
                    switch piece {
                    case let .word(text):
                        Text(text).font(.toeflyBodyLarge).foregroundStyle(Color.toefly.ink)
                            // The paragraph is read once as a whole (below), not word by word.
                            .accessibilityHidden(true)
                    case let .gap(index, prefix, tail):
                        gap(index: index, prefix: prefix, tail: tail)
                    }
                }
            }
            .accessibilityElement(children: .contain)
        }
    }

    private enum Piece {
        case word(String)
        case gap(Int, String, String)
    }

    /// "Yet th… blank 1 sup… blank 2": the text for VoiceOver with the gaps in place.
    private var spokenParagraph: String {
        Cloze.parse(model.stimulus.markup ?? "").map { segment in
            switch segment {
            case let .text(text): text
            case let .gap(index, prefix): "\(prefix), blank \(index + 1),"
            }
        }.joined()
    }

    /// Words keep their trailing space so the flow wraps between them; punctuation right after a gap stays with it.
    private var pieces: [Piece] {
        var result: [Piece] = []
        var glued = false
        for segment in Cloze.parse(model.stimulus.markup ?? "") {
            switch segment {
            case let .gap(index, prefix):
                result.append(.gap(index, prefix, ""))
                glued = true
            case let .text(text):
                var words = text.split(separator: " ", omittingEmptySubsequences: false).map(String.init)
                if glued, let first = words.first, !first.isEmpty, case let .gap(index, prefix, _)? = result.last {
                    result[result.count - 1] = .gap(index, prefix, first + (words.count > 1 ? " " : ""))
                    words.removeFirst()
                }
                glued = false
                for (offset, word) in words.enumerated() {
                    let value = offset == words.count - 1 ? word : "\(word) "
                    if !value.isEmpty {
                        result.append(.word(value))
                    }
                }
            }
        }
        return result
    }

    @ViewBuilder private func gap(index: Int, prefix: String, tail: String) -> some View {
        if model.items.indices.contains(index) {
            let item = model.items[index]
            let length = item.content.missingLetters ?? 1
            HStack(spacing: 1) {
                Text(prefix).font(.toeflyBodyLarge).foregroundStyle(Color.toefly.ink)
                GapField(model: model, item: item) {
                    focused = index + 1 < model.items.count ? index + 1 : nil
                }
                .font(.toeflyBodyLarge.monospaced())
                .foregroundStyle(Color.toefly.ink)
                .keyboardType(.asciiCapable)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.none)
                .focused($focused, equals: index)
                .submitLabel(index + 1 < model.items.count ? .next : .done)
                .onSubmit { focused = index + 1 < model.items.count ? index + 1 : nil }
                .frame(width: CGFloat(length) * letterWidth + 8, height: 30)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(focused == index ? Color.toefly.primary : Color.toefly.borderStrong)
                        .frame(height: 1.5)
                }
                .accessibilityLabel(
                    "Gap \(index + 1) of \(model.items.count), word starts with \(prefix), \(length) letters",
                )
                .accessibilityIdentifier("gap-\(index + 1)")
                if !tail.isEmpty {
                    Text(tail).font(.toeflyBodyLarge).foregroundStyle(Color.toefly.ink).accessibilityHidden(true)
                }
            }
            .frame(minHeight: 36)
        }
    }
}

/// One gap: the text lives in the field and is cleaned on every change, so a refused character never shows
/// (a binding to an unchanged model value would not redraw the field).
private struct GapField: View {
    let model: TaskPlayerViewModel
    let item: AttemptItem
    let filled: () -> Void
    @State private var text = ""

    var body: some View {
        TextField("", text: $text)
            .onAppear { text = model.gapText(item) }
            .onChange(of: text) { _, value in
                let length = item.content.missingLetters ?? 1
                let clean = Cloze.sanitize(value, length: length)
                if clean != value {
                    text = clean
                    return
                }
                if clean != model.gapText(item) {
                    model.setGap(item, text: clean)
                    if clean.count >= length {
                        filled()
                    }
                }
            }
    }
}

// MARK: - Questions

/// `McqOption` (docs/07-ux-ui.md §3.11): a row button with a radio mark; the choice gets a 2 pt `primary` border and
/// `brand-50` in Practice.
struct OptionsView: View {
    let options: [ChoiceOption]
    let selected: [String]
    let choose: (String) -> Void

    var body: some View {
        VStack(spacing: Space.s2) {
            ForEach(options, id: \.key) { option in
                let isSelected = selected.contains(option.key)
                Button {
                    choose(option.key)
                } label: {
                    HStack(alignment: .firstTextBaseline, spacing: Space.s3) {
                        Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(isSelected ? Color.toefly.primary : Color.toefly.checkboxBorder)
                            .accessibilityHidden(true)
                        Text(option.text)
                            .font(.toeflyBody)
                            .foregroundStyle(Color.toefly.ink)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, 14)
                    .padding(.horizontal, Space.s4)
                    .frame(minHeight: 44)
                    .background(
                        isSelected ? Color.toefly.brand50 : Color.toefly.surfaceTask,
                        in: .rect(cornerRadius: Radius.base),
                    )
                    .overlay {
                        RoundedRectangle(cornerRadius: Radius.base)
                            .strokeBorder(
                                isSelected ? Color.toefly.primary : Color.toefly.border,
                                lineWidth: isSelected ? 2 : 1,
                            )
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(option.key). \(option.text)")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
                .accessibilityIdentifier("option-\(option.key)")
            }
        }
    }
}

/// The prompt and the options of the current item.
struct QuestionView: View {
    let model: TaskPlayerViewModel

    var body: some View {
        if let item = model.currentItem {
            VStack(alignment: .leading, spacing: Space.s3) {
                if let intro = item.content.introSentence {
                    Text(intro).font(.toeflyBody).padding(Space.s3)
                        .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.sm))
                }
                if let prompt = item.content.prompt {
                    Text(prompt)
                        .font(.toeflyBodyStrong)
                        .foregroundStyle(Color.toefly.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("question-prompt")
                }
                OptionsView(
                    options: item.content.options ?? [],
                    selected: model.answer(for: item)?.optionIds ?? [],
                    choose: model.choose,
                )
            }
            .id(item.id)
        }
    }
}

// MARK: - Read in Daily Life

/// AC-07.02.1, AC-07.02.4: the text in the frame of its genre above the question, foldable into "Show text".
struct DailyLifePlayer: View {
    let model: TaskPlayerViewModel
    @State private var showsText = true

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            DisclosureGroup(isExpanded: $showsText) {
                DailyLifeText(stimulus: model.stimulus).padding(.top, Space.s2)
            } label: {
                Text(showsText ? "Hide text" : "Show text").font(.toeflyCaption.weight(.semibold))
            }
            .tint(Color.toefly.inkMuted)
            .accessibilityIdentifier("daily-life-text")
            QuestionView(model: model)
        }
    }
}

struct DailyLifeText: View {
    let stimulus: Stimulus

    var body: some View {
        if let messages = stimulus.messages, !messages.isEmpty {
            VStack(alignment: .leading, spacing: Space.s2) {
                if let title = stimulus.title {
                    Text(title).font(.toeflyTitle3)
                }
                ForEach(Array(messages.enumerated()), id: \.offset) { index, message in
                    VStack(alignment: index.isMultiple(of: 2) ? .leading : .trailing, spacing: 2) {
                        Text([message.sender, message.time].compactMap(\.self).joined(separator: " · "))
                            .font(.toeflyMicro)
                            .foregroundStyle(Color.toefly.inkMuted)
                        Text(message.text)
                            .font(.toeflyBody)
                            .padding(.horizontal, Space.s3)
                            .padding(.vertical, Space.s2)
                            .background(
                                index.isMultiple(of: 2) ? Color.toefly.surfaceSunken : Color.toefly.accentSoft,
                                in: .rect(cornerRadius: Radius.lg),
                            )
                    }
                    .frame(maxWidth: .infinity, alignment: index.isMultiple(of: 2) ? .leading : .trailing)
                }
            }
        } else {
            let email = stimulus.genre == "EMAIL"
            VStack(alignment: .leading, spacing: Space.s3) {
                if email, let headers = stimulus.headers {
                    Grid(alignment: .leading, horizontalSpacing: Space.s3, verticalSpacing: 2) {
                        ForEach(["From", "To", "Subject"].filter { headers[$0] != nil }, id: \.self) { key in
                            GridRow {
                                Text(key).font(.toeflyCaption.weight(.semibold)).foregroundStyle(Color.toefly.inkMuted)
                                Text(headers[key] ?? "").font(.toeflyCaption)
                            }
                        }
                    }
                    Divider()
                }
                if let title = stimulus.title {
                    Text(title).font(.toeflyTitle3)
                }
                ForEach(Array((stimulus.body ?? "").split(separator: "\n").enumerated()), id: \.offset) { _, line in
                    Text(String(line)).font(.toeflyBody).fixedSize(horizontal: false, vertical: true)
                }
            }
            .foregroundStyle(Color.toefly.ink)
            .padding(Space.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.base))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.base)
                    .strokeBorder(email ? Color.toefly.border : Color.toefly.borderStrong, lineWidth: email ? 1 : 2)
            }
        }
    }
}

// MARK: - Read an Academic Passage

/// AC-07.03.1–3: Passage / Question segments; a vocabulary question highlights its word, an insert-sentence question
/// puts four markers into the text.
struct PassagePlayer: View {
    let model: TaskPlayerViewModel
    @State private var tab = Tab.question

    private enum Tab: String, CaseIterable { case passage = "Passage", question = "Question" }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            Picker("Show", selection: $tab) {
                ForEach(Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("passage-tabs")
            switch tab {
            case .passage:
                passage
            case .question:
                if let insertion = model.currentItem?.content.insertion {
                    VStack(alignment: .leading, spacing: Space.s2) {
                        Text("Where would this sentence best fit?").font(.toeflyCaption.weight(.semibold))
                            .foregroundStyle(Color.toefly.inkMuted)
                        Text(insertion.sentence).font(.toeflyBodyStrong)
                        Text("Open the passage and tap a square ■ to place it.")
                            .font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                    }
                    .padding(Space.s4)
                    .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.base))
                    if let chosen = model.currentItem.flatMap({ model.answer(for: $0)?.optionIds.first }) {
                        Label("Position \(chosen) chosen", systemImage: "checkmark.circle")
                            .font(.toeflyCaption).foregroundStyle(Color.toefly.primary)
                    }
                    Button("Open the passage") { tab = .passage }.buttonStyle(.toefly(.secondary))
                } else {
                    QuestionView(model: model)
                }
            }
        }
        .onChange(of: model.index) {
            if model.currentItem?.content.targetWord != nil || model.currentItem?.content.insertion != nil {
                tab = model.currentItem?.content.insertion != nil ? .question : tab
            }
        }
    }

    private var passage: some View {
        let content = model.currentItem?.content
        let insertion = content?.insertion
        let chosen = model.currentItem.flatMap { model.answer(for: $0)?.optionIds.first }
        return VStack(alignment: .leading, spacing: Space.s3) {
            if let title = model.stimulus.title {
                Text(title).font(.toeflyTitle3).accessibilityAddTraits(.isHeader)
            }
            ForEach(Array((model.stimulus.paragraphs ?? []).enumerated()), id: \.offset) { index, text in
                let pieces = Passage.pieces(
                    of: text,
                    paragraph: index,
                    markers: insertion?.markers ?? [],
                    highlight: content?.targetWord,
                )
                FlowLayout(spacing: 4, lineSpacing: 4) {
                    ForEach(Array(pieces.enumerated()), id: \.offset) { _, piece in
                        switch piece {
                        case let .text(value, highlighted):
                            Text(value)
                                .font(.toeflyBodyLarge)
                                .foregroundStyle(highlighted ? Color.toefly.accentOnSoft : Color.toefly.ink)
                                .padding(.horizontal, highlighted ? 2 : 0)
                                .background(highlighted ? Color.toefly.accentSoft : .clear, in: .rect(cornerRadius: 4))
                                .fixedSize(horizontal: false, vertical: true)
                        case let .marker(key):
                            marker(key, chosen: chosen == key, sentence: insertion?.sentence ?? "")
                        }
                    }
                }
            }
        }
        .foregroundStyle(Color.toefly.ink)
    }

    @ViewBuilder private func marker(_ key: String, chosen: Bool, sentence: String) -> some View {
        Button {
            model.choose(key)
        } label: {
            Text("■")
                .font(.body.weight(.bold))
                .foregroundStyle(chosen ? Color.toefly.primary : Color.toefly.ink)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Insert the sentence at position \(key)")
        .accessibilityAddTraits(chosen ? .isSelected : [])
        .accessibilityIdentifier("marker-\(key)")
        if chosen {
            Text(sentence)
                .font(.toeflyBodyLarge.weight(.medium))
                .foregroundStyle(Color.toefly.accentOnSoft)
                .padding(.horizontal, 4)
                .background(Color.toefly.accentSoft, in: .rect(cornerRadius: 4))
        }
    }
}

// MARK: - Listening

/// docs/02 EP-08: the context line and the audio first; the questions after the first play. Listen and Choose a
/// Response plays one line per item with no text on screen.
struct ListeningPlayer: View {
    let model: TaskPlayerViewModel
    @State private var notes = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            VStack(spacing: Space.s3) {
                Image(systemName: "headphones")
                    .font(.system(size: 48, weight: .light))
                    .foregroundStyle(Color.toefly.accent)
                    .accessibilityHidden(true)
                Text(model.layout == .chooseResponse ? "Listen to the speaker." : (model.stimulus.contextLine ?? ""))
                    .font(.toeflyTitle3)
                    .foregroundStyle(Color.toefly.ink)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            AudioPlayerView(
                url: model.audioURL,
                autoplay: model.autoplaysAudio,
                paused: model.isCovered,
                onFinished: model.audioFinished,
                onReplay: model.countReplay,
                onPlayingChange: model.setAudioPlaying,
            )
            .id(model.audioURL)
            if model.questionsVisible {
                if model.layout == .chooseResponse {
                    Text("Choose the best response.").font(.toeflyBodyStrong)
                }
                QuestionView(model: model)
            } else {
                Text("The questions appear after the audio.")
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier("questions-after-audio")
            }
            if model.attempt?.taskType == .listenAcademicTalk {
                VStack(alignment: .leading, spacing: Space.s1) {
                    Text("Notes").font(.toeflyCaption.weight(.semibold)).foregroundStyle(Color.toefly.inkMuted)
                    TextEditor(text: $notes)
                        .autocorrectionDisabled()
                        .frame(minHeight: 88)
                        .scrollContentBackground(.hidden)
                        .padding(Space.s2)
                        .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.sm))
                        .accessibilityLabel("Notes")
                    Text("Your notes stay on this screen while you work.")
                        .font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                }
            }
        }
    }
}

// MARK: - Build a Sentence

/// `DragChunks` (docs/07-ux-ui.md §3.10, US-09.01): the line of the other speaker, the slots between fixed words and
/// the bank. Tap a fragment to place it in the first free slot, tap a placed one to return it, or drag it onto a slot;
/// VoiceOver gets "Place in slot N" actions (WCAG 2.5.7).
struct SentencePlayer: View {
    let model: TaskPlayerViewModel

    var body: some View {
        if let item = model.currentItem {
            let slots = model.slots(for: item)
            let chunks = item.content.chunks ?? []
            VStack(alignment: .leading, spacing: Space.s4) {
                Text("Make an appropriate sentence.").font(.toeflyBodyStrong).foregroundStyle(Color.toefly.inkMuted)
                if let context = item.content.contextLine {
                    Text(context)
                        .font(.toeflyBody)
                        .padding(.horizontal, Space.s3)
                        .padding(.vertical, Space.s2)
                        .background(Color.toefly.surfaceSunken, in: .rect(cornerRadius: Radius.lg))
                        .accessibilityLabel("They say: \(context)")
                }
                FlowLayout(spacing: Space.s2, lineSpacing: Space.s2) {
                    ForEach(Array(parts(item, slots: slots).enumerated()), id: \.offset) { _, part in
                        switch part {
                        case let .fixed(text):
                            Text(text).font(.toeflyBodyLarge).foregroundStyle(Color.toefly.ink)
                        case let .slot(index, chunk):
                            slot(index, chunk: chunk.flatMap { id in chunks.first { $0.id == id } })
                        }
                    }
                }
                .padding(Space.s3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.base))
                FlowLayout(spacing: Space.s2, lineSpacing: Space.s2) {
                    ForEach(Slots.bank(chunks, slots: slots)) { chunk in
                        Button {
                            model.placeChunk(chunk.id)
                        } label: {
                            ChunkPill(text: chunk.text)
                        }
                        .buttonStyle(.plain)
                        .draggable(chunk.id)
                        .accessibilityHint("Places it in the first free slot")
                        .accessibilityActions {
                            ForEach(slots.indices, id: \.self) { index in
                                Button("Place in slot \(index + 1)") { model.placeChunk(chunk.id, slot: index) }
                            }
                        }
                        .accessibilityIdentifier("chunk-\(chunk.text)")
                    }
                }
                .accessibilityElement(children: .contain)
                .accessibilityLabel("Fragments")
                Button("Reset", systemImage: "arrow.counterclockwise") { model.resetSentence() }
                    .buttonStyle(.toefly(.ghost, size: .sm))
            }
        }
    }

    private enum Part {
        case fixed(String)
        case slot(Int, String?)
    }

    private func parts(_ item: AttemptItem, slots: [String?]) -> [Part] {
        var index = 0
        return (item.content.template ?? []).map { part in
            if part.kind == .fixed {
                return .fixed(part.text ?? "")
            }
            defer { index += 1 }
            return .slot(index, slots.indices.contains(index) ? slots[index] : nil)
        }
    }

    private func slot(_ index: Int, chunk: Chunk?) -> some View {
        Group {
            if let chunk {
                Button {
                    model.removeChunk(chunk.id)
                } label: {
                    ChunkPill(text: chunk.text, placed: true)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Slot \(index + 1): \(chunk.text)")
                .accessibilityHint("Returns it to the fragments")
            } else {
                RoundedRectangle(cornerRadius: Radius.sm)
                    .strokeBorder(Color.toefly.borderStrong, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    .frame(width: 72, height: 40)
                    .accessibilityElement()
                    .accessibilityLabel("Slot \(index + 1), empty")
            }
        }
        .dropDestination(for: String.self) { ids, _ in
            guard let id = ids.first else { return false }
            model.placeChunk(id, slot: index)
            return true
        }
        .accessibilityIdentifier("slot-\(index + 1)")
    }
}

private struct ChunkPill: View {
    let text: String
    var placed = false

    var body: some View {
        HStack(spacing: Space.s1) {
            if !placed {
                Image(systemName: "line.3.horizontal").font(.caption2).foregroundStyle(Color.toefly.inkMuted)
                    .accessibilityHidden(true)
            }
            Text(text).font(.toeflyBody.weight(.medium)).foregroundStyle(Color.toefly.ink)
        }
        .padding(.horizontal, Space.s3)
        .frame(minHeight: 40)
        .background(placed ? Color.toefly.accentSoft : Color.toefly.surface, in: .capsule)
        .overlay { Capsule().strokeBorder(placed ? Color.toefly.primary : Color.toefly.borderStrong) }
    }
}
