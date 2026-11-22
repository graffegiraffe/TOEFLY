import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Review of a closed task (US-11.01, docs/07-ux-ui.md §5.10): the score ring, the time against the norm, the change
/// of the section estimate, then every question with the answers and why each option is right or wrong. Missed
/// questions open first.
struct ReviewView: View {
    let model: ReviewViewModel
    let askTutor: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    content
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, 96)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(model.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.load() }
    }

    @ViewBuilder private var content: some View {
        switch model.loadState {
        case .loading:
            Skeleton(height: 140)
            Skeleton(height: 220)
        case let .failed(error):
            ErrorState(error: error) { Task { await model.load() } }
        case .loaded:
            Text(model.title)
                .font(.toeflyTitle1)
                .foregroundStyle(Color.toefly.ink)
                .accessibilityAddTraits(.isHeader)
            summary
            if model.isCloze {
                clozeReview
            } else {
                Text("Questions").font(.toeflyTitle3).accessibilityAddTraits(.isHeader)
                ForEach(model.questions) { QuestionReview(row: $0) }
            }
            if !model.transcript.isEmpty {
                transcript
            }
            HStack(spacing: Space.s2) {
                Button(action: askTutor) { ButtonLabel(title: "Ask tutor", systemImage: "sparkles") }
                    .buttonStyle(.toefly(.secondary))
                    .accessibilityIdentifier("review-ask-tutor")
                Button("Done") { dismiss() }
                    .buttonStyle(.toefly(.ghost))
                    .accessibilityIdentifier("review-done")
            }
        }
    }

    private var summary: some View {
        Card(large: true) {
            // One layout at a time: ViewThatFits would keep both copies in the accessibility tree.
            let layout = dynamicTypeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: Space.s4))
                : AnyLayout(HStackLayout(alignment: .center, spacing: Space.s5))
            layout { score; time }
            if let ability = model.abilityText {
                Label(ability, systemImage: "chart.line.uptrend.xyaxis")
                    .font(.toeflyBodyStrong)
                    .foregroundStyle(Color.toefly.primary)
                    .accessibilityIdentifier("review-ability")
            }
            if let pace = model.paceText {
                Label(pace, systemImage: "speedometer").font(.toeflyCaption).foregroundStyle(Color.toefly.warning)
            }
            if let replays = model.replayText {
                Label(replays, systemImage: "arrow.counterclockwise")
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("review-summary")
    }

    private var score: some View {
        HStack(spacing: Space.s3) {
            ProgressRing(fraction: model.scoreFraction, diameter: 74) {
                Text("\(model.review?.correct ?? 0)").font(.toeflyTitle2.monospacedDigit())
            }
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Score").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                Text(model.scoreText).font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
                    .accessibilityIdentifier("review-score")
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var time: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Time").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            Text(model.timeText).font(.toeflyBodyStrong.monospacedDigit()).foregroundStyle(Color.toefly.ink)
        }
        .accessibilityElement(children: .combine)
    }

    /// AC-11.01.4: every gap in the paragraph, green when right, red with the right spelling otherwise.
    private var clozeReview: some View {
        Card {
            Text("Your answers").font(.toeflyTitle3).accessibilityAddTraits(.isHeader)
            ForEach(model.gaps) { gap in
                HStack(alignment: .firstTextBaseline, spacing: Space.s2) {
                    Image(systemName: gap.correct ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundStyle(gap.correct ? Color.toefly.success : Color.toefly.danger)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(gap.prefix)\(gap.expected)").font(.toeflyBodyStrong)
                        if !gap.correct {
                            Text(gap.typed.isEmpty ? "No answer" : "You wrote “\(gap.prefix)\(gap.typed)”")
                                .font(.toeflyCaption)
                                .foregroundStyle(Color.toefly.danger)
                        }
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(
                    "Gap \(gap.number): \(gap.prefix)\(gap.expected), \(gap.correct ? "correct" : "wrong")",
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("review-gaps")
    }

    private var transcript: some View {
        Card {
            DisclosureGroup("Show transcript") {
                VStack(alignment: .leading, spacing: Space.s2) {
                    ForEach(Array(model.transcript.enumerated()), id: \.offset) { _, line in
                        Text(line.speaker.isEmpty ? line.text : "\(line.speaker): \(line.text)")
                            .font(.toeflyBody)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.top, Space.s2)
            }
            .font(.toeflyBodyStrong)
            .tint(Color.toefly.ink)
            .accessibilityIdentifier("review-transcript")
        }
    }
}

/// One question in the review state (docs/07-ux-ui.md §3.11).
private struct QuestionReview: View {
    let row: ReviewViewModel.QuestionRow
    @State private var expanded: Bool

    init(row: ReviewViewModel.QuestionRow) {
        self.row = row
        _expanded = State(initialValue: !row.correct)
    }

    var body: some View {
        Card {
            DisclosureGroup(isExpanded: $expanded) {
                VStack(alignment: .leading, spacing: Space.s3) {
                    if let prompt = row.prompt {
                        Text(prompt).font(.toeflyBodyStrong).fixedSize(horizontal: false, vertical: true)
                    }
                    ForEach(row.options) { option in
                        OptionReview(option: option)
                    }
                    if let yours = row.yourSentence, let right = row.rightSentence {
                        sentence("Your sentence", yours, correct: row.correct)
                        if !row.correct {
                            sentence("Right sentence", right, correct: true)
                        }
                    }
                    if let explanation = row.explanation, !explanation.text.isEmpty {
                        VStack(alignment: .leading, spacing: Space.s1) {
                            HStack(spacing: Space.s1) {
                                Text(whyTitle).font(.toeflyCaption.weight(.semibold))
                                if explanation.isAI {
                                    Pill(text: "AI explanation")
                                }
                            }
                            Text(explanation.text).font(.toeflyBody).fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(Space.s3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.toefly.successSoft, in: .rect(cornerRadius: Radius.sm))
                    }
                }
                .padding(.top, Space.s2)
            } label: {
                HStack(spacing: Space.s2) {
                    Text("Question \(row.number)").font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
                    Pill(text: row.correct ? "Correct" : "Missed", style: row.correct ? .accent : .muted)
                }
            }
            .tint(Color.toefly.ink)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("review-question-\(row.number)")
    }

    private var whyTitle: String {
        if let key = row.options.first(where: { $0.state == .correct })?.key {
            return "Why \(key) is correct"
        }
        return "Why this is correct"
    }

    private func sentence(_ title: String, _ text: String, correct: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.toeflyCaption.weight(.semibold)).foregroundStyle(Color.toefly.inkMuted)
            Text(text).font(.toeflyBody).foregroundStyle(correct ? Color.toefly.success : Color.toefly.danger)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct OptionReview: View {
    let option: ReviewViewModel.OptionRow

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            HStack(alignment: .firstTextBaseline, spacing: Space.s2) {
                Text(option.key).font(.toeflyBodyStrong)
                Text(option.text).font(.toeflyBody).frame(maxWidth: .infinity, alignment: .leading)
            }
            switch option.state {
            case .correct:
                Label(
                    option.chosen ? "Your answer · Correct answer" : "Correct answer",
                    systemImage: "checkmark.circle",
                )
                .font(.toeflyCaption.weight(.semibold))
                .foregroundStyle(Color.toefly.success)
            case .wrongChoice:
                Label("Your answer", systemImage: "xmark.circle")
                    .font(.toeflyCaption.weight(.semibold))
                    .foregroundStyle(Color.toefly.danger)
            case .neutral:
                EmptyView()
            }
            if let whyNot = option.whyNot {
                Text("Why not: \(whyNot)").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .foregroundStyle(Color.toefly.ink)
        .padding(Space.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: .rect(cornerRadius: Radius.base))
        .accessibilityElement(children: .combine)
    }

    private var background: Color {
        switch option.state {
        case .correct: Color.toefly.successSoft
        case .wrongChoice: Color.toefly.dangerSoft
        case .neutral: Color.toefly.surfaceTask
        }
    }
}
