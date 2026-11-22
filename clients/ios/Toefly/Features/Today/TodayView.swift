import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Today (US-05.01, docs/07-ux-ui.md §5.5): Overdue, the tasks of the day with their times and the total, the words to
/// review, "Start" for the first open task; a finished day offers to get ahead.
struct TodayView: View {
    let model: TodayViewModel
    let route: AppRouteBuilder

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    Text(model.caption)
                        .font(.toeflyCaption)
                        .foregroundStyle(Color.toefly.inkMuted)
                        .accessibilityIdentifier("today-caption")
                    content
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, 96)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .refreshable { await model.load() }
        }
        .navigationTitle("Today")
        .task { await model.load() }
    }

    @ViewBuilder private var content: some View {
        switch model.loadState {
        case .loading:
            VStack(spacing: Space.s3) {
                Skeleton(height: 72)
                Skeleton(height: 72)
                Skeleton(height: 72)
            }
            .accessibilityElement()
            .accessibilityLabel("Loading")
        case .noPlan:
            EmptyState(
                systemImage: "sun.max",
                title: "Your plan is on its way",
                text: "Your tasks for the day appear here once your plan is built.",
            )
        case let .failed(error):
            ErrorState(error: error) { Task { await model.load() } }
        case .loaded:
            if let waiting = model.waitingText {
                Banner(kind: .info, text: waiting, systemImage: "person.badge.clock")
            }
            if !model.overdue.isEmpty {
                group(
                    title: "Overdue",
                    systemImage: "clock.badge.exclamationmark",
                    tasks: model.overdue,
                    id: "today-overdue",
                )
            }
            if model.isDayDone {
                doneCard
            }
            if !model.tasks.isEmpty {
                group(title: model.tasksTitle, systemImage: nil, tasks: model.tasks, id: "today-tasks")
            }
            if let vocabulary = model.vocabularyText {
                vocabularyCard(vocabulary)
            }
            if let task = model.startTask {
                NavigationLink(value: route(task)) {
                    ButtonLabel(title: "Start", systemImage: "play.fill")
                }
                .buttonStyle(.toefly(.primary, size: .lg, block: true))
                .accessibilityHint("Opens \(task.title)")
                .accessibilityIdentifier("today-start")
            }
        }
    }

    private func group(title: String, systemImage: String?, tasks: [WeekTask], id: String) -> some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            HStack(spacing: Space.s1) {
                if let systemImage {
                    Image(systemName: systemImage).accessibilityHidden(true)
                }
                Text(title)
            }
            .font(.toeflyTitle3)
            .foregroundStyle(Color.toefly.ink)
            .accessibilityAddTraits(.isHeader)
            ForEach(tasks) { task in
                TaskLink(task: task, route: route) {
                    WeekTaskRow(task: task)
                        .padding(.horizontal, Space.s4)
                        .padding(.vertical, Space.s1)
                        .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.lg))
                        .overlay { RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color.toefly.border) }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("today-task-\(task.title)")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(id)
    }

    /// AC-05.01.2: "You're done for today" and two tasks of the next day.
    private var doneCard: some View {
        Card(large: true) {
            EmptyState(
                systemImage: "checkmark.seal",
                title: "You're done for today",
                text: model.getAhead
                    .isEmpty ? "Come back tomorrow for the next tasks." : "Get ahead with tomorrow's tasks.",
            )
            ForEach(model.getAhead) { task in
                TaskLink(task: task, route: route) { WeekTaskRow(task: task) }
                    .buttonStyle(.plain)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("today-done")
    }

    private func vocabularyCard(_ text: String) -> some View {
        HStack(spacing: Space.s3) {
            IconBox(systemImage: WeekSectionStyle.symbol(.vocabulary), size: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(text).font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
                Text("Vocabulary review opens in the next update")
                    .font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
            }
            Spacer()
        }
        .padding(Space.s4)
        .background(Color.toefly.surface, in: .rect(cornerRadius: Radius.lg))
        .overlay { RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color.toefly.border) }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("today-vocabulary")
    }
}
