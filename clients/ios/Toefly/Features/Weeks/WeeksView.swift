import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// Weeks gallery (US-04.09, docs/07-ux-ui.md §5.6): the current week first, upcoming ones, completed ones folded.
struct WeeksView: View {
    @Bindable var model: WeeksViewModel

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    if !model.subtitle.isEmpty {
                        Text(model.subtitle).font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted)
                    }
                    content
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, 96)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .refreshable { await model.load() }
        }
        .navigationTitle("Weeks")
        .task { await model.load() }
    }

    @ViewBuilder private var content: some View {
        switch model.loadState {
        case .loading:
            VStack(spacing: Space.s3) {
                Skeleton(height: 220)
                Skeleton(height: 220)
            }
            .accessibilityElement()
            .accessibilityLabel("Loading")
        case .noPlan:
            EmptyState(
                systemImage: "calendar",
                title: "Your plan is on its way",
                text: "Weeks appear once your plan is built after the onboarding.",
            )
        case let .failed(error):
            ErrorState(error: error) { Task { await model.load() } }
        case .loaded:
            if let current = model.current {
                WeekCardView(card: current)
            }
            if !model.upcoming.isEmpty {
                Overline(text: "Upcoming").padding(.top, Space.s2)
                ForEach(model.upcoming) { WeekCardView(card: $0) }
            }
            if !model.completed.isEmpty {
                DisclosureGroup(isExpanded: $model.showsCompleted) {
                    VStack(spacing: Space.s3) {
                        ForEach(model.completed) { WeekCardView(card: $0) }
                    }
                    .padding(.top, Space.s2)
                } label: {
                    Text("Completed weeks (\(model.completed.count))")
                        .font(.toeflyBodyStrong)
                        .foregroundStyle(Color.toefly.ink)
                }
                .tint(Color.toefly.ink)
                .accessibilityIdentifier("completed-weeks")
            }
        }
    }
}

/// `WeekCard` (docs/07-ux-ui.md §3.2): 16:9 cover, "Week 7", dates, a thin progress bar, status badges.
struct WeekCardView: View {
    let card: WeeksViewModel.Card

    var body: some View {
        if card.week.isOpenable {
            NavigationLink(value: AppRoute.week(card.week.id)) { content }
                .buttonStyle(.plain)
                .accessibilityIdentifier("week-card-\(card.week.weekNo)")
        } else {
            content
                .accessibilityIdentifier("week-card-\(card.week.weekNo)")
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            Cover(id: card.coverId, aspectRatio: 16 / 7)
                .overlay(alignment: .topLeading) {
                    HStack(spacing: Space.s1) {
                        ForEach(card.badges, id: \.self, content: badge)
                    }
                    .padding(Space.s3)
                }
            VStack(alignment: .leading, spacing: Space.s1) {
                Text(card.title).font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
                Text(card.dates).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                if let progress = card.progress {
                    ProgressBar(value: progress, label: "\(card.title) progress", thin: true, showsPercent: true)
                        .padding(.top, Space.s1)
                }
                if let note = card.note {
                    Text(note).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                }
            }
            .padding(Space.s4)
        }
        .background(Color.toefly.surface)
        .clipShape(.rect(cornerRadius: Radius.lg))
        .overlay { RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Color.toefly.border) }
        .contentShape(.rect(cornerRadius: Radius.lg))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private func badge(_ badge: WeeksViewModel.Badge) -> some View {
        switch badge {
        case .current: Pill(text: "Current", style: .solid)
        case .done: Pill(text: "Done", systemImage: "checkmark", style: .solid)
        case .upcoming: Pill(text: "Upcoming", style: .solid)
        case .mockExam: Pill(text: "Mock exam", style: .exam)
        case .locked: Pill(text: "Pro", systemImage: "lock", style: .solid)
        }
    }
}

/// The week page (US-04.02, docs/07-ux-ui.md §5.7): cover, "Week N", properties, the Today strip, the tasks by section
/// and the gallery of sections.
struct WeekView: View {
    let model: WeekViewModel
    let route: AppRouteBuilder
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Cover(id: model.coverId, height: 180)
                    VStack(alignment: .leading, spacing: Space.s4) {
                        content
                    }
                    .padding(.horizontal, Space.s4)
                    .padding(.top, Space.s4)
                    .padding(.bottom, 96)
                    .frame(maxWidth: 720)
                    .frame(maxWidth: .infinity)
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable { await model.load() }
        }
        .navigationTitle(model.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .task { await model.load() }
    }

    @ViewBuilder private var content: some View {
        switch model.loadState {
        case .loading:
            Skeleton(height: 40, width: 160)
            Skeleton(height: 120)
            Skeleton(height: 220)
        case let .failed(error):
            ErrorState(error: error) { Task { await model.load() } }
        case .loaded:
            Text(model.title)
                .font(.toeflyTitle1)
                .foregroundStyle(Color.toefly.ink)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("week-title")
            properties
            todayStrip
            if !model.rebalancedTasks.isEmpty {
                rebalanceBanner
            }
            Text("This week").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink).accessibilityAddTraits(.isHeader)
            ForEach(model.groups, id: \.section) { group in
                sectionGroup(group.section, tasks: group.tasks)
            }
            Text("Sections").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink).accessibilityAddTraits(.isHeader)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: Space.s3)], spacing: Space.s3) {
                ForEach(model.sectionCards) { card in
                    NavigationLink(value: AppRoute.section(weekId: model.weekId, section: card.section)) {
                        SectionCardView(card: card)
                    }
                    .buttonStyle(.plain)
                }
            }
            weeklyReview
        }
    }

    private var properties: some View {
        Card {
            LabeledContent("Date", value: model.dates)
            Divider().overlay(Color.toefly.border)
            LabeledContent("Total progress") {
                ProgressBar(value: model.week?.progressPercent ?? 0, label: "Total progress", showsPercent: true)
                    .frame(maxWidth: 200)
            }
            if let status = model.statusText {
                Divider().overlay(Color.toefly.border)
                LabeledContent("Status", value: status)
            }
            if let rationale = model.week?.rationale {
                Divider().overlay(Color.toefly.border)
                Text(rationale).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            }
        }
        .font(.toeflyBody)
        .foregroundStyle(Color.toefly.ink)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("week-properties")
    }

    /// The first open task of the day on glass (§5.7 "Today").
    @ViewBuilder private var todayStrip: some View {
        if let task = model.nextTask, !model.isClosed {
            GlassPanel {
                let layout = dynamicTypeSize.isAccessibilitySize
                    ? AnyLayout(VStackLayout(alignment: .leading, spacing: Space.s3))
                    : AnyLayout(HStackLayout(spacing: Space.s3))
                layout {
                    stripText(task)
                    if !dynamicTypeSize.isAccessibilitySize {
                        Spacer(minLength: 0)
                    }
                    startButton(task)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("week-today")
        }
    }

    private func stripText(_ task: WeekTask) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Today: \(task.title)").font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
            if let caption = model.nextTaskCaption {
                Text(caption).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            }
        }
    }

    private func startButton(_ task: WeekTask) -> some View {
        TaskLink(task: task, route: route) { Text("Start") }
            .buttonStyle(.toefly(.primary))
            .accessibilityIdentifier("week-start")
    }

    private var rebalanceBanner: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Banner(
                kind: .info,
                text: "We rebalanced this week so you can catch up.",
                systemImage: "arrow.triangle.2.circlepath",
            )
            DisclosureGroup("See what changed") {
                VStack(alignment: .leading, spacing: Space.s1) {
                    ForEach(model.rebalancedTasks) { task in
                        Text("Left out: \(task.title)").font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .font(.toeflyBodyStrong)
        }
    }

    /// One group of the week table on iPhone: the section tag, then its tasks in the order of the days.
    private func sectionGroup(_ section: WeekSection, tasks: [WeekTask]) -> some View {
        Card {
            SectionTag(section: section).accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) {
                ForEach(tasks) { task in
                    TaskLink(task: task, route: route) {
                        WeekTaskRow(task: task, day: model.dayLabel(task))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("week-task-\(task.title)")
                    if task.id != tasks.last?.id {
                        Divider().overlay(Color.toefly.border)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var weeklyReview: some View {
        if model.week != nil {
            HStack(spacing: Space.s3) {
                IconBox(systemImage: "doc.text.magnifyingglass")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Weekly review").font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
                    Text(model.weeklyReviewText)
                        .font(.toeflyCaption)
                        .foregroundStyle(Color.toefly.inkMuted)
                }
            }
            .padding(.top, Space.s2)
            .accessibilityElement(children: .combine)
        }
    }
}

/// `SectionCard` (docs/07-ux-ui.md §3.2): mini cover, icon, title, "3 of 4 done" and the done mark.
struct SectionCardView: View {
    let card: WeekViewModel.SectionCard

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Cover(id: card.coverId, aspectRatio: 16 / 7)
            VStack(alignment: .leading, spacing: Space.s1) {
                HStack {
                    IconBox(systemImage: WeekSectionStyle.symbol(card.section), size: 30)
                    Spacer()
                    Image(systemName: card.isComplete ? "checkmark.square.fill" : "square")
                        .font(.title3)
                        .foregroundStyle(card.isComplete ? Color.toefly.primary : Color.toefly.checkboxBorder)
                        .accessibilityHidden(true)
                }
                Text(card.section.label).font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
                Text(card.caption).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
            }
            .padding(Space.s3)
        }
        .background(Color.toefly.surface)
        .clipShape(.rect(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).strokeBorder(Color.toefly.border) }
        .accessibilityElement(children: .combine)
        .accessibilityValue(card.isComplete ? "Done" : card.caption)
        .accessibilityIdentifier("section-card-\(card.section.rawValue.lowercased())")
    }
}

/// Section page (docs/07-ux-ui.md §5.8): the tasks of one section by day.
struct SectionView: View {
    let model: WeekViewModel
    let section: WeekSection
    let route: AppRouteBuilder

    var body: some View {
        ZStack {
            MeshBackground(variant: .app)
            List {
                Section {
                    VStack(alignment: .leading, spacing: Space.s2) {
                        Cover(id: Covers.section(section), height: 120)
                            .clipShape(.rect(cornerRadius: Radius.base))
                        Text(model.card(section).caption).font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted)
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                ForEach(model.days(of: section), id: \.title) { day in
                    Section(day.title) {
                        ForEach(day.tasks) { task in
                            TaskLink(task: task, route: route) { WeekTaskRow(task: task) }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .navigationTitle(model.sectionTitle(section))
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.load() }
    }
}
