import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Onboarding: six steps on a glass panel over `mesh-auth`, "Step N of 6" and a progress bar on top, "Back" and
/// "Continue" at the bottom (docs/07-ux-ui.md §5.3). Texts are the same as the web wizard.
struct OnboardingView: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        ZStack {
            MeshBackground(variant: .auth)
            ScrollView {
                GlassPanel {
                    header
                    if model.isLoading {
                        ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Loading")
                    } else {
                        stepContent
                            .id(model.step)
                            .transition(.opacity)
                    }
                    if let error = model.errorMessage {
                        Banner(kind: .danger, text: error).accessibilityIdentifier("onboarding-error")
                    }
                    footer
                }
                .padding(.horizontal, Space.s4)
                .padding(.vertical, Space.s6)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .animation(Motion.base, value: model.step)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            HStack {
                Text(model.stepLabel)
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
                    .accessibilityIdentifier("onboarding-step")
                Spacer()
                Logo(font: .headline.weight(.heavy))
            }
            ProgressBar(value: model.progress * 100, label: "Onboarding progress")
        }
    }

    @ViewBuilder private var stepContent: some View {
        switch model.step {
        case .goal: GoalStep(model: model)
        case .examDate: ExamDateStep(model: model)
        case .schedule: ScheduleStep(model: model)
        case .explanationLanguage: LanguageStep(model: model)
        case .consents: ConsentsStep(model: model)
        case .diagnosticIntro: DiagnosticIntroStep(model: model)
        }
    }

    private var footer: some View {
        ViewThatFits(in: .horizontal) {
            HStack {
                backButton
                Spacer()
                continueButton
            }
            VStack(spacing: Space.s2) {
                continueButton
                backButton
            }
        }
    }

    @ViewBuilder private var backButton: some View {
        if model.step.previous != nil {
            Button { model.back() } label: { ButtonLabel(title: "Back", systemImage: "chevron.left") }
                .buttonStyle(.toefly(.ghost))
                .disabled(!model.canGoBack)
                .accessibilityIdentifier("onboarding-back")
        }
    }

    private var continueButton: some View {
        Button { Task { await model.continueTapped() } } label: {
            ButtonLabel(title: model.continueTitle, isLoading: model.isSaving)
        }
        .buttonStyle(.toefly(.primary))
        .disabled(!model.canContinue)
        .accessibilityIdentifier("onboarding-continue")
    }
}

private struct StepTitle: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            Text(title).font(.toeflyTitle2).foregroundStyle(Color.toefly.ink).accessibilityAddTraits(.isHeader)
            if let subtitle {
                Text(subtitle).font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted)
            }
        }
    }
}

/// US-02.01: overall band or targets per section, with CEFR and the comparable score.
private struct GoalStep: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(title: "What score do you need?", subtitle: "Overall target on the 1–6 scale of the TOEFL iBT.")
            if !model.setPerSection {
                ChoiceGrid(
                    label: "Overall target",
                    options: Band.all,
                    selection: $model.overallTarget,
                    title: Band.format,
                    spokenTitle: Band.spokenHint,
                )
            }
            Banner(kind: .info, text: model.goalHint, systemImage: "graduationcap")
                .accessibilityIdentifier("band-hint")
            Text("Comparable score (reference until January 2028)").font(.toeflyCaption)
                .foregroundStyle(Color.toefly.inkMuted)
            Toggle("Set per section", isOn: $model.setPerSection)
                .toggleStyle(.toeflyCheckbox)
                .accessibilityIdentifier("per-section")
            if model.setPerSection {
                VStack(spacing: Space.s2) {
                    sectionPicker("Reading", value: $model.sectionTargets.reading)
                    sectionPicker("Listening", value: $model.sectionTargets.listening)
                    sectionPicker("Writing", value: $model.sectionTargets.writing)
                    sectionPicker("Speaking", value: $model.sectionTargets.speaking)
                }
            }
        }
    }

    private func sectionPicker(_ title: String, value: Binding<Double>) -> some View {
        LabeledContent(title) {
            Picker(title, selection: value) {
                ForEach(Band.all, id: \.self) { band in
                    Text(Band.format(band)).tag(band)
                }
            }
            .pickerStyle(.menu)
        }
        .font(.toeflyBody)
        .frame(minHeight: 44)
    }
}

/// AC-02.01.3, AC-02.01.4: date from tomorrow to five years ahead, or "not booked yet".
private struct ExamDateStep: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(title: "When is your exam?")
            DatePicker(
                "Exam date",
                selection: Binding(
                    get: { model.examDate ?? model.examDateRange.lowerBound },
                    set: { model.examDate = $0 },
                ),
                in: model.examDateRange,
                displayedComponents: .date,
            )
            .datePickerStyle(.graphical)
            .tint(Color.toefly.primary)
            .disabled(model.examNotBooked)
            .opacity(model.examNotBooked ? 0.45 : 1)
            .accessibilityIdentifier("exam-date")
            if let error = model.examDateError {
                Banner(kind: .danger, text: error)
            } else if let warning = model.examDateWarning {
                Banner(kind: .warning, text: warning)
            }
            Toggle("I haven’t booked the exam yet", isOn: $model.examNotBooked)
                .toggleStyle(.toeflyCheckbox)
                .accessibilityIdentifier("exam-not-booked")
            if model.examNotBooked {
                Text("We’ll build an 8-week plan.").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            }
        }
    }
}

/// US-02.02: minutes a day, the 15-minute warning and the reminder time.
private struct ScheduleStep: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(title: "How much time can you study?")
            ChoiceGrid(
                label: "Minutes a day",
                options: OnboardingRules.minuteOptions,
                selection: $model.minutesPerDay,
                title: { "\($0)" },
                spokenTitle: { "\($0) minutes" },
                minimumWidth: 56,
            )
            if let warning = model.minutesWarning {
                Banner(kind: .warning, text: warning).accessibilityIdentifier("minutes-warning")
            }
            DatePicker("Daily reminder at", selection: studyTime, displayedComponents: .hourAndMinute)
                .font(.toeflyBody)
                .accessibilityIdentifier("reminder-time")
            Banner(kind: .info, text: model.reminderNote, systemImage: "bell")
        }
    }

    private var studyTime: Binding<Date> {
        Binding(
            get: {
                let parts = model.studyTime.split(separator: ":").compactMap { Int($0) }
                let components = DateComponents(hour: parts.first ?? 19, minute: parts.count > 1 ? parts[1] : 0)
                return Calendar.current.date(from: components) ?? .now
            },
            set: { date in
                let components = Calendar.current.dateComponents([.hour, .minute], from: date)
                model.studyTime = String(format: "%02d:%02d", components.hour ?? 19, components.minute ?? 0)
            },
        )
    }
}

/// US-02.03: explanations language and the time zone, prefilled from the device.
private struct LanguageStep: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(
                title: "Explanations language",
                subtitle: "Reviews and the AI tutor explain mistakes in this language.",
            )
            VStack(spacing: Space.s2) {
                option(.en, title: "English")
                option(.ru, title: "Russian")
            }
            LabeledContent("Time zone") {
                Picker("Time zone", selection: $model.timezone) {
                    ForEach(OnboardingViewModel.timeZoneIdentifiers, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.menu)
            }
            .font(.toeflyBody)
            .frame(minHeight: 44)
            .accessibilityIdentifier("time-zone")
        }
    }

    private func option(_ language: ExplanationLanguage, title: String) -> some View {
        let selected = model.explanationLanguage == language
        return Button { model.explanationLanguage = language } label: {
            HStack(spacing: Space.s3) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(selected ? Color.toefly.primary : Color.toefly.checkboxBorder)
                    .accessibilityHidden(true)
                Text(title).font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
                Spacer()
            }
            .padding(.horizontal, Space.s4)
            .frame(minHeight: 48)
            .background(selected ? Color.toefly.accentSoft : Color.toefly.surface, in: .rect(cornerRadius: Radius.sm))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.sm)
                    .strokeBorder(selected ? Color.toefly.primary : Color.toefly.border)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
        .accessibilityIdentifier("language-\(language.rawValue)")
    }
}

/// "Your data": AI processing and product emails (docs/07-ux-ui.md §5.3, docs/10-security-privacy.md).
private struct ConsentsStep: View {
    static let aiProcessingHint = "Your texts and recordings are checked by external AI providers "
        + "after personal data is masked. Without it, AI reviews and the tutor chat are off."

    @Bindable var model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(title: "Your data")
            Card {
                Toggle(isOn: $model.aiProcessing) {
                    VStack(alignment: .leading, spacing: Space.s1) {
                        Text("AI processing").font(.toeflyBodyStrong)
                        Text(Self.aiProcessingHint)
                            .font(.toeflyCaption)
                            .foregroundStyle(Color.toefly.inkMuted)
                    }
                }
                .toggleStyle(.switch)
                .tint(Color.toefly.primary)
                .accessibilityIdentifier("consent-ai")
            }
            Card {
                Toggle("Product emails and tips", isOn: $model.productEmails)
                    .font(.toeflyBodyStrong)
                    .toggleStyle(.switch)
                    .tint(Color.toefly.primary)
                    .accessibilityIdentifier("consent-emails")
            }
        }
    }
}

/// Last step: summary and "Continue to diagnostic" (AC-02.04.1).
private struct DiagnosticIntroStep: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            StepTitle(
                title: "Take your 25-minute diagnostic",
                subtitle: "It sets your starting level, then we build your plan week by week up to the exam.",
            )
            Card {
                ForEach(model.summary, id: \.label) { row in
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(row.label).foregroundStyle(Color.toefly.inkMuted)
                            Spacer()
                            Text(row.value).fontWeight(.semibold).foregroundStyle(Color.toefly.ink)
                        }
                        VStack(alignment: .leading) {
                            Text(row.label).foregroundStyle(Color.toefly.inkMuted)
                            Text(row.value).fontWeight(.semibold).foregroundStyle(Color.toefly.ink)
                        }
                    }
                    .font(.toeflyBody)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}
