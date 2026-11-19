import SwiftUI
import ToeflyFeatures

/// Five tabs of the student with the system iOS 26 `TabView` (glass from the system) and the floating AI Tutor
/// button over the tab bar on the right (AC-05.03.2, docs/07-ux-ui.md §4.3).
struct MainTabView: View {
    @Bindable var app: AppModel

    var body: some View {
        TabView(selection: $app.selectedTab) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Tab(tab.title, systemImage: tab.symbol, value: tab) {
                    NavigationStack {
                        screen(for: tab)
                    }
                }
                .accessibilityIdentifier("tab-\(tab.rawValue)")
            }
        }
        .overlay(alignment: .bottomTrailing) {
            TutorFab { app.isTutorPresented = true }
                .padding(.trailing, Space.s4)
                .padding(.bottom, 72)
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
        .accessibilityIdentifier("coming-soon-\(tab.rawValue)")
    }

    private var title: String {
        "\(tab.title) opens in the next update"
    }

    private var text: String {
        switch tab {
        case .today: "Your tasks for the day, in any order, with the time for each."
        case .weeks: "Every week of your plan with its cover, progress and sections."
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
        .accessibilityIdentifier("tutor-sheet")
    }
}
