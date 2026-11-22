import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Task player (docs/07-ux-ui.md §5.9) for the closed types in Practice: a header with the countdown, Hide timer and
/// Pause; the task on `surface-task`; a footer with the question dots, the save state and Back / Next / Check answers.
/// The time stops in the background (`scenePhase`, AC-16.01.2) and while paused, when the task is blurred.
struct TaskPlayerView: View {
    @Bindable var model: TaskPlayerViewModel
    let onSubmitted: (AttemptReview) -> Void
    let askTutor: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            switch model.loadState {
            case .loading:
                ProgressView("Opening the task…").frame(maxWidth: .infinity, maxHeight: .infinity)
            case let .failed(error):
                ErrorState(error: error) { Task { await model.load() } }
                    .padding(Space.s4)
                    .frame(maxHeight: .infinity, alignment: .top)
            case .ready:
                player
            }
        }
        .background(Color.toefly.surfaceTask.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar { toolbar }
        .task { await model.load() }
        .task(id: model.loadState) {
            guard model.loadState == .ready else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                await model.tick()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            Task { await model.setForeground(phase == .active) }
        }
        .onChange(of: model.review) { _, review in
            if let review {
                onSubmitted(review)
            }
        }
        .alert("Submit anyway?", isPresented: $model.confirmingSubmit) {
            Button("Keep working", role: .cancel) {}
            Button("Submit") { Task { await model.submit() } }
                .accessibilityIdentifier("submit-anyway")
        } message: {
            let count = model.unansweredCount
            Text("\(count) question\(count == 1 ? "" : "s") unanswered. Unanswered questions count as wrong.")
        }
    }

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button {
                Task {
                    await model.exit()
                    dismiss()
                }
            } label: {
                Label("Save and exit", systemImage: "chevron.backward")
            }
            .accessibilityIdentifier("player-exit")
        }
        ToolbarItem(placement: .principal) {
            if let question = model.questionText {
                Text(question).font(.toeflyCaption.weight(.semibold)).foregroundStyle(Color.toefly.inkMuted)
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if model.loadState == .ready {
                timer
                Button {
                    Task { model.paused ? await model.resume() : await model.pause() }
                } label: {
                    Image(systemName: model.paused ? "play.fill" : "pause.fill")
                }
                .accessibilityLabel(model.paused ? "Resume" : "Pause")
                .accessibilityIdentifier("player-pause")
                Menu {
                    Button(
                        model.timerHidden ? "Show timer" : "Hide timer",
                        systemImage: model.timerHidden ? "eye" : "eye.slash",
                    ) { model.timerHidden.toggle() }
                    Button("Ask Toefly Tutor", systemImage: "sparkles", action: askTutor)
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("More")
                .accessibilityIdentifier("player-more")
            }
        }
    }

    @ViewBuilder private var timer: some View {
        if !model.timerHidden {
            let clock = model.timer
            Label(clock.text, systemImage: "timer")
                .labelStyle(.titleAndIcon)
                .font(.toeflyBody.monospacedDigit().weight(.semibold))
                .foregroundStyle(clock.overtime ? Color.toefly.warning : Color.toefly.ink)
                .accessibilityLabel(clock.overtime ? "Over the recommended time" : "Recommended time left")
                .accessibilityValue(clock.text)
                .accessibilityIdentifier("player-timer")
        }
    }

    private var player: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.s3) {
                Text(model.typeTitle)
                    .font(.toeflyTitle3)
                    .foregroundStyle(Color.toefly.ink)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("player-title")
                PlayerContent(model: model)
            }
            .padding(Space.s4)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
            .blur(radius: model.isCovered ? 14 : 0)
            .allowsHitTesting(!model.isCovered)
            .accessibilityHidden(model.isCovered)
        }
        .scrollDismissesKeyboard(.interactively)
        .onScrollPhaseChange { _, _ in model.input() }
        .simultaneousGesture(TapGesture().onEnded { model.input() })
        .overlay {
            if model.isCovered {
                cover
            }
        }
        .safeAreaInset(edge: .bottom) { footer }
    }

    /// AC-06.01.3 / AC-16.01.3: the paused task or "are you still here?".
    private var cover: some View {
        VStack(spacing: Space.s3) {
            Text(model.paused ? "Paused" : "Paused — are you still here?")
                .font(.toeflyTitle3)
                .foregroundStyle(Color.toefly.ink)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(model.paused ? "The timer and your time are stopped." : "Time is not counted while you are away.")
                .font(.toeflyBody)
                .foregroundStyle(Color.toefly.inkMuted)
                .multilineTextAlignment(.center)
            Button {
                Task { model.paused ? await model.resume() : model.wake() }
            } label: {
                ButtonLabel(title: "Resume", systemImage: "play.fill")
            }
            .buttonStyle(.toefly(.primary))
            .accessibilityIdentifier("player-resume")
        }
        .padding(Space.s6)
        .frame(maxWidth: 360)
        .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.lg))
        .overlay { RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color.toefly.border) }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player-paused")
    }

    private var footer: some View {
        VStack(spacing: Space.s2) {
            if model.submitOffline {
                Text("You're offline — we'll submit when you're back.")
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.warning)
            } else if let error = model.submitError {
                Text(error.message).font(.toeflyCaption).foregroundStyle(Color.toefly.danger)
            }
            if model.layout?.isSinglePage == false, model.items.count > 1 {
                dots
            }
            HStack(spacing: Space.s2) {
                Text(saveText)
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
                    .accessibilityIdentifier("player-save-state")
                Spacer(minLength: 0)
                if model.layout?.isSinglePage == false, model.index > 0 {
                    Button("Back") { model.previous() }
                        .buttonStyle(.toefly(.ghost))
                        .accessibilityIdentifier("player-back")
                }
                if model.isLastQuestion {
                    Button {
                        Task { await model.requestSubmit() }
                    } label: {
                        ButtonLabel(
                            title: model.submitting ? "Checking…" : "Check answers",
                            systemImage: "checkmark",
                            isLoading: model.submitting,
                        )
                    }
                    .buttonStyle(.toefly(.primary))
                    .disabled(model.submitting || model.paused)
                    .accessibilityIdentifier("player-submit")
                } else {
                    Button {
                        model.next()
                    } label: {
                        ButtonLabel(title: "Next", systemImage: "chevron.forward")
                    }
                    .buttonStyle(.toefly(.primary))
                    .accessibilityIdentifier("player-next")
                }
            }
        }
        .padding(.horizontal, Space.s4)
        .padding(.vertical, Space.s3)
        .background(Color.toefly.surface)
        .overlay(alignment: .top) { Divider().overlay(Color.toefly.border) }
    }

    /// Question dots: answered ones filled, the current one ringed (§5.9).
    private var dots: some View {
        HStack(spacing: Space.s2) {
            ForEach(Array(model.items.enumerated()), id: \.element.id) { index, item in
                let answered = model.isAnswered(item)
                Button {
                    model.go(to: index)
                } label: {
                    Circle()
                        .fill(answered ? Color.toefly.primary : Color.clear)
                        .overlay { Circle().strokeBorder(
                            answered ? Color.toefly.primary : Color.toefly.borderStrong,
                            lineWidth: 2,
                        ) }
                        .frame(width: 12, height: 12)
                        .padding(3)
                        .overlay {
                            if index == model.index {
                                Circle().strokeBorder(Color.toefly.primary, lineWidth: 1.5)
                            }
                        }
                        .frame(minWidth: 28, minHeight: 28)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Question \(index + 1)\(answered ? ", answered" : "")")
                .accessibilityAddTraits(index == model.index ? .isSelected : [])
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Questions")
    }

    private var saveText: String {
        switch model.saveState {
        case .idle: ""
        case .saving: "Saving…"
        case .saved: "Saved"
        case .offline: "Saved on this device"
        }
    }
}

/// The task area of each layout (docs/07-ux-ui.md §5.9, items 1–8).
private struct PlayerContent: View {
    @Bindable var model: TaskPlayerViewModel

    var body: some View {
        switch model.layout {
        case .cloze:
            ClozePlayer(model: model)
        case .dailyLife:
            DailyLifePlayer(model: model)
        case .passage:
            PassagePlayer(model: model)
        case .chooseResponse, .setAudio:
            ListeningPlayer(model: model)
        case .buildSentence:
            SentencePlayer(model: model)
        case nil:
            EmptyState(systemImage: "hourglass", title: "This task opens in the next update", text: model.title)
        }
    }
}
