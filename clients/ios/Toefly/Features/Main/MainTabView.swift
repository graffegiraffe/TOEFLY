import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Five tabs of the student with the system iOS 26 `TabView` (glass from the system) and the floating AI Tutor
/// button over the tab bar on the right (AC-05.03.2, docs/07-ux-ui.md §4.3).
struct MainTabView: View {
    @Bindable var app: AppModel

    var body: some View {
        TabView(selection: $app.selectedTab) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Tab(tab.title, systemImage: tab.symbol, value: tab) {
                    stack(for: tab)
                }
                .accessibilityIdentifier("tab-\(tab.rawValue)")
            }
        }
        .overlay(alignment: .bottomTrailing) {
            // The player has its own "Ask Toefly Tutor" in the toolbar and needs the bottom edge for its footer.
            if !app.isPlayerOpen {
                TutorFab { app.isTutorPresented = true }
                    .padding(.trailing, Space.s4)
                    .padding(.bottom, 72)
            }
        }
        .sheet(isPresented: $app.isTutorPresented) {
            TutorSheet(tab: app.selectedTab)
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $app.isProfilePresented) {
            NavigationStack {
                ProfileView(app: app)
            }
        }
    }

    @ViewBuilder private func stack(for tab: AppTab) -> some View {
        switch tab {
        case .today:
            NavigationStack(path: $app.todayPath) {
                if let today = app.today {
                    TodayView(model: today, route: app.route(for:))
                        .navigationDestination(for: AppRoute.self) { RouteView(app: app, route: $0, tab: .today) }
                }
            }
        case .weeks:
            NavigationStack(path: $app.weeksPath) {
                if let weeks = app.weeks {
                    WeeksView(model: weeks)
                        .navigationDestination(for: AppRoute.self) { RouteView(app: app, route: $0, tab: .weeks) }
                }
            }
        default:
            NavigationStack {
                screen(for: tab)
            }
        }
    }

    @ViewBuilder private func screen(for tab: AppTab) -> some View {
        switch tab {
        case .home:
            if let home = app.home {
                HomeView(model: home, profileTapped: { app.isProfilePresented = true }, openToday: app.openToday)
            }
        case .today, .weeks, .library, .progress:
            ComingSoonView(tab: tab)
        }
    }
}

/// Tabs whose screens arrive in the next sub-phases (12b–12d).
struct ComingSoonView: View {
    let tab: AppTab

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            EmptyState(systemImage: tab.symbol, title: title, text: text)
        }
        .navigationTitle(tab.title)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("coming-soon-\(tab.rawValue)")
    }

    private var title: String {
        "\(tab.title) opens in the next update"
    }

    private var text: String {
        switch tab {
        case .today, .weeks: ""
        case .library: "Grammar, vocabulary and practice by task type."
        case .progress: "Your band forecast, time spent and progress by section."
        case .home: ""
        }
    }
}

/// Context panel of Toefly Tutor; the streaming chat arrives with phase 12c.
struct TutorSheet: View {
    let tab: AppTab
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: Space.s4) {
                HStack(spacing: Space.s2) {
                    Image(systemName: "sparkles").foregroundStyle(Color.toefly.accent).accessibilityHidden(true)
                    Text("Toefly Tutor").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
                }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isHeader)
                Text("The tutor chat opens in the next update. "
                    + "It will see that you are on \(tab.title) and answer about it.")
                    .font(.toeflyBody)
                    .foregroundStyle(Color.toefly.inkMuted)
                Spacer()
            }
            .padding(Space.s5)
            .frame(maxWidth: .infinity, alignment: .leading)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", systemImage: "xmark") { dismiss() }
                        .accessibilityIdentifier("tutor-close")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tutor-sheet")
    }
}

/// A screen of the Today or Weeks stack (docs/07-ux-ui.md §4.3).
struct RouteView: View {
    let app: AppModel
    let route: AppRoute
    let tab: AppTab

    var body: some View {
        switch route {
        case let .week(id):
            WeekView(model: app.weekModel(id), route: app.route(for:))
        case let .section(weekId, section):
            SectionView(model: app.weekModel(weekId), section: section, route: app.route(for:))
        case let .task(task):
            TaskScreen(app: app, task: task, tab: tab)
        case let .review(attemptId):
            ReviewView(model: app.reviewModel(attemptId)) { app.isTutorPresented = true }
        }
    }
}

/// A task: the player of a closed item set, or a note that it opens in a later update (AC-05.01.4).
struct TaskScreen: View {
    let app: AppModel
    let task: WeekTask
    let tab: AppTab
    @State private var player: TaskPlayerViewModel?

    init(app: AppModel, task: WeekTask, tab: AppTab) {
        self.app = app
        self.task = task
        self.tab = tab
        _player = State(initialValue: TaskDestination.of(task) == .player ? app.playerModel(for: task) : nil)
    }

    var body: some View {
        if let player {
            TaskPlayerView(
                model: player,
                onSubmitted: { app.showReview($0, in: tab) },
                askTutor: { app.isTutorPresented = true },
            )
            .onAppear { app.isPlayerOpen = true }
            .onDisappear { app.isPlayerOpen = false }
        } else {
            ZStack {
                MeshBackground(variant: .app)
                EmptyState(systemImage: WeekSectionStyle.symbol(task.section), title: task.title, text: laterText)
            }
            .navigationTitle(task.section.label)
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("task-later")
        }
    }

    private var laterText: String {
        if case let .later(text) = TaskDestination.of(task) {
            return text
        }
        return "This task opens in the next update of TOEFLY."
    }
}
