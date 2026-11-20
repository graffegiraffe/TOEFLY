import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Home (US-05.02): greeting, the Today block with the first three tasks, week progress, goal and the score forecast;
/// before the plan exists the diagnostic leads (AC-02.04.4). Profile and settings open from the avatar (AC-05.03.2).
struct HomeView: View {
    let model: HomeViewModel
    let profileTapped: () -> Void
    let openToday: () -> Void

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    Text(model.greeting)
                        .font(.toeflyTitle1)
                        .foregroundStyle(Color.toefly.ink)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("home-greeting")
                    content
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, 96) // room for the tutor button
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .refreshable { await model.load() }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: profileTapped) {
                    Avatar(initials: model.profile?.initials ?? "", size: 32)
                }
                .accessibilityLabel("Profile and settings")
                .accessibilityIdentifier("profile-button")
            }
        }
        .task { await model.load() }
    }

    @ViewBuilder private var content: some View {
        switch model.loadState {
        case .loading:
            loading
        case let .failed(error):
            ErrorState(error: error) { Task { await model.load() } }
        case .loaded:
            if model.showsDiagnostic {
                diagnosticCard
            }
            if model.hasPlan {
                todayCard
                weekCard
            }
            if model.isStudent {
                goalCard
                forecastCard
            }
        }
    }

    private var loading: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Skeleton(height: 120)
            Skeleton(height: 80)
            Skeleton(height: 80)
        }
        .accessibilityElement()
        .accessibilityLabel("Loading")
    }

    /// AC-02.04.4: "Take your 25-minute diagnostic" while the plan is not built.
    private var diagnosticCard: some View {
        Card(large: true) {
            HStack(alignment: .top, spacing: Space.s3) {
                IconBox(systemImage: "stethoscope", size: 44)
                VStack(alignment: .leading, spacing: Space.s1) {
                    Text("Take your 25-minute diagnostic").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
                    Text("Your plan starts after the diagnostic. It opens in the next update of TOEFLY.")
                        .font(.toeflyBody)
                        .foregroundStyle(Color.toefly.inkMuted)
                }
            }
            Button("Start diagnostic") {}
                .buttonStyle(.toefly(.primary, block: true))
                .disabled(true)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("home-diagnostic")
    }

    /// AC-05.02.1: the first three tasks of Today and the button to open Today.
    private var todayCard: some View {
        Card(large: true) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Today").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink).accessibilityAddTraits(.isHeader)
                if let caption = [model.todayCaption, model.todayMinutesText].compactMap(\.self)
                    .joined(separator: " · ").nilIfEmpty {
                    Text(caption).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                }
            }
            if model.topTasks.isEmpty {
                Text("You're done for today").font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted)
            } else {
                VStack(spacing: 0) {
                    ForEach(model.topTasks) { task in
                        TaskRow(task: task)
                        if task.id != model.topTasks.last?.id {
                            Divider().overlay(Color.toefly.border)
                        }
                    }
                }
            }
            Button(action: openToday) { ButtonLabel(title: "Open Today", systemImage: "sun.max") }
                .buttonStyle(.toefly(.primary, block: true))
                .accessibilityIdentifier("open-today")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("home-today")
    }

    @ViewBuilder private var weekCard: some View {
        if let week = model.week, let title = model.weekTitle {
            Card {
                Overline(text: title, systemImage: "calendar")
                ProgressBar(value: week.progressPercent, label: title, showsPercent: true)
                if let mock = model.nextMockExamText {
                    Text(mock).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("home-week")
        }
    }

    private var goalCard: some View {
        Card {
            Overline(text: "Your goal", systemImage: "target")
            if let hint = model.goalHint {
                Text(hint).font(.toeflyTitle2).foregroundStyle(Color.toefly.ink).accessibilityIdentifier("home-goal")
            } else {
                Skeleton(height: 28, width: 160)
            }
            Text(model.examText).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            if let minutes = model.goalMinutes {
                HStack(spacing: Space.s3) {
                    ProgressRing(fraction: Double(minutes.done) / Double(max(minutes.goal, 1))) { EmptyView() }
                    VStack(alignment: .leading) {
                        Text("\(minutes.done) / \(minutes.goal) min").font(.toeflyBodyStrong.monospacedDigit())
                        Text("Goal today").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Goal today: \(minutes.done) of \(minutes.goal) minutes")
            }
        }
    }

    /// P6: the forecast shows "Need more data" until assessment-service has enough answers.
    private var forecastCard: some View {
        Card {
            BandRingInsufficient(detail: "Finish the diagnostic to see your predicted score.")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("home-forecast")
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
