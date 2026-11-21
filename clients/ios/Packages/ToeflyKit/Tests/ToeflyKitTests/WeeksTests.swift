import Foundation
import Testing
@testable import ToeflyCore
@testable import ToeflyFeatures

@MainActor
@Suite("Weeks and Today")
struct WeeksTests {
    @Test("AC-04.01.3 AC-04.09.1 the gallery: current week first, upcoming ones built later, completed ones folded")
    func ac04013Gallery() async {
        let fixture = fixtureRepositories(.withPlan)
        let model = WeeksViewModel(planning: fixture.planning)
        await model.load()
        #expect(model.loadState == .loaded)
        #expect(model.subtitle == "8-week plan")
        let current = model.current
        #expect(current?.title == "Week 3")
        #expect(current?.badges == [.current])
        #expect(current?.progress == 46.6)
        #expect(current?.coverId == "week-03-lecture-hall")
        #expect(model.upcoming.map(\.title) == ["Week 4", "Week 5", "Week 6", "Week 7", "Week 8"])
        #expect(model.upcoming.first?.badges == [.upcoming, .mockExam])
        #expect(model.upcoming.first?.note == "Will be built from your progress")
        #expect(model.upcoming.first?.progress == nil)
        #expect(model.upcoming.first?.coverId == "week-04-exam-desk")
        #expect(model.completed.map(\.title) == ["Week 2", "Week 1"])
        #expect(model.completed.first?.badges == [.done])
        #expect(model.completed.first?.dates == "Nov 9 – Nov 15")
    }

    @Test("AC-04.02.4 weeks waiting for the tutor or locked cannot be opened")
    func ac04024Notes() {
        let week = { (status: WeekStatus, locked: Bool) in
            WeekSummary(
                id: UUID(), weekNo: 5, status: status, startDate: Date(), endDate: Date(), progressPercent: nil,
                mockExam: false, locked: locked,
            )
        }
        #expect(!week(.awaitingTutorApproval, false).isOpenable)
        #expect(!week(.published, true).isOpenable)
        #expect(week(.published, false).isOpenable)
        #expect(Covers.week(20, mockExam: false) == "week-05-headphones", "the rotation skips the exam cover")
        #expect(Covers.week(1, mockExam: false) == "week-01-campus")
        #expect(Covers.section(.grammar) == "section-grammar")
    }

    @Test("No plan yet: the gallery and Today say so")
    func noPlan() async {
        let fixture = fixtureRepositories(.onboarded)
        let weeks = WeeksViewModel(planning: fixture.planning)
        await weeks.load()
        #expect(weeks.loadState == .noPlan)
        #expect(weeks.subtitle.isEmpty)
        let today = TodayViewModel(planning: fixture.planning)
        await today.load()
        #expect(today.loadState == .noPlan)
    }

    @Test("A failed load keeps the error")
    func failed() async {
        let planning = FailingPlanning()
        let weeks = WeeksViewModel(planning: planning)
        await weeks.load()
        #expect(weeks.loadState == .failed(.offline))
        let week = WeekViewModel(weekId: UUID(), planning: planning)
        await week.load()
        #expect(week.loadState == .failed(.offline))
        #expect(week.title == "Week")
        #expect(week.dates.isEmpty)
        #expect(week.groups.isEmpty)
        #expect(week.days(of: .reading).isEmpty)
        #expect(week.coverId == "week-01-campus")
        let today = TodayViewModel(planning: planning)
        await today.load()
        #expect(today.loadState == .failed(.offline))
        #expect(today.tasksTitle == "Today's tasks")
    }

    @Test("AC-04.02.1 AC-04.02.2 the week page: dates, progress, Today, tables by section and the gallery")
    func ac04021WeekPage() async throws {
        let fixture = fixtureRepositories(.withPlan)
        let model = WeekViewModel(weekId: weekThree, planning: fixture.planning, calendar: gregorianMinsk)
        await model.load()
        #expect(model.loadState == .loaded)
        #expect(model.title == "Week 3")
        #expect(model.dates == "Nov 16 – Nov 22, 2026")
        #expect(model.statusText == nil)
        #expect(!model.isClosed)
        #expect(model.week?.rationale?.hasPrefix("More listening") == true)
        #expect(model.nextTask?.title == "Complete the Words: Coral reefs")
        #expect(model.nextTaskCaption == "Reading · about 3 min · 4 tasks today")
        #expect(model.groups.map(\.section) == WeekSection.allCases)
        let reading = try #require(model.groups.first?.tasks)
        #expect(reading.map(\.title).first == "Daily life: Campus Gym Email", "in the order of the days")
        #expect(reading.first?.provenance?.label == "From book")
        #expect(model.dayLabel(reading[0]) == "Tue")
        #expect(model.sectionCards.count == 6)
        #expect(model.card(.reading).caption == "1 of 3 done")
        #expect(model.card(.vocabulary).isComplete)
        #expect(model.card(.resources).caption == "0 of 1 done")
        #expect(model.rebalancedTasks.isEmpty)
        #expect(model.coverId == "week-03-lecture-hall")
        let days = model.days(of: .listening)
        #expect(days.map(\.title) == ["Thursday · day 4", "Saturday · day 6", "Sunday · day 7"])
        #expect(model.sectionTitle(.listening) == "Listening · Week 3")
        #expect(model.weeklyReviewText == "Available on Nov 22")
    }

    @Test("AC-04.02.3 skipped tasks by a rebalance leave the counts; statuses of closed weeks")
    func ac04023Counts() {
        let task = { (status: WeekTaskStatus, reason: WeekTaskSkipReason?) in
            WeekTask(
                id: UUID(),
                title: "t",
                section: .reading,
                status: status,
                skippedReason: reason,
                estimatedMinutes: 3,
            )
        }
        #expect(!task(.skipped, .rebalance).countsInProgress)
        #expect(task(.skipped, .userSkipped).countsInProgress)
        #expect(task(.done, nil).countsInProgress)
        #expect(WeekTaskSkipReason.noEntitlement.leavesProgress)
        #expect(!WeekTaskSkipReason.weekClose.leavesProgress)
        #expect(Provenance.generated.label == "AI-generated")
        #expect(Provenance.tutorCreated.label == "From your tutor")
        #expect(TaskType.allCases.allSatisfy { !$0.label.isEmpty })
    }

    @Test("AC-05.01.1 Today: the tasks of the day with times and the total, then the words to review")
    func ac05011Today() async {
        let fixture = fixtureRepositories(.withPlan)
        let model = TodayViewModel(planning: fixture.planning, now: { testNow })
        await model.load()
        #expect(model.loadState == .loaded)
        #expect(model.caption.hasSuffix("· Week 3, day 4"))
        #expect(model.overdue.isEmpty)
        #expect(model.tasks.map(\.title) == [
            "Complete the Words: Coral reefs",
            "Academic talk: Biology",
            "Take an interview: Remote work",
            "Email: Missing a Meeting",
        ])
        #expect(model.tasksTitle == "Today's tasks · about 24 min")
        #expect(model.vocabularyText == "12 words to review")
        #expect(model.waitingText == nil)
        #expect(!model.isDayDone)
        #expect(model.getAhead.isEmpty)
        #expect(model.startTask?.title == "Complete the Words: Coral reefs")
    }

    @Test("AC-04.04.1 tasks of past days stay in the Overdue group above today")
    func ac04041Overdue() async {
        let fixture = fixtureRepositories(.behind)
        let model = TodayViewModel(planning: fixture.planning, now: { testNow })
        await model.load()
        #expect(model.overdue.map(\.title) == ["Conversation: Library hours"])
        #expect(model.overdue.first?.overdueDays == 1)
        #expect(model.startTask?.title == "Conversation: Library hours")
        #expect(model.tasksTitle == "Today's tasks · about 26 min")
    }

    @Test("AC-05.01.2 a finished day offers to get ahead")
    func ac05012GetAhead() async throws {
        let harness = PlayerHarness()
        for title in ["Complete the Words: Coral reefs", "Academic talk: Biology"] {
            let player = try await harness.player(title)
            await player.submit()
            #expect(player.review != nil)
        }
        // Speaking and Writing tasks close with phase 12c; here the day is "done" for what the fixture can play.
        let model = TodayViewModel(planning: harness.fixture.planning)
        await model.load()
        #expect(!model.isDayDone)
        #expect(model.tasks.prefix(2).allSatisfy { $0.status == .done })
        #expect(model.startTask == nil, "the open tasks of the day do not open on iOS yet")
        let done = TodaySummary(
            weekNo: 3, dayIndex: 4,
            overdue: [],
            today: [WeekTask(id: UUID(), title: "t", section: .reading, status: .done, estimatedMinutes: 3)],
            getAhead: [WeekTask(id: UUID(), title: "next", section: .reading, status: .pending, estimatedMinutes: 3)],
            vocabularyDue: 1, waitingForTutor: true, estimatedMinutes: 3, doneMinutes: 3,
        )
        let summary = TodayViewModel(planning: StaticPlanning(today: done))
        await summary.load()
        #expect(summary.isDayDone)
        #expect(summary.getAhead.map(\.title) == ["next"])
        #expect(summary.vocabularyText == "1 word to review")
        #expect(summary.waitingText == "Your tutor is preparing Week 4")
    }

    @Test("AC-05.01.4 a task opens the player, its review when done, or waits for a later update")
    func ac05014Destinations() async throws {
        let fixture = fixtureRepositories(.withPlan)
        let week = try await fixture.planning.week(id: weekThree)
        let task = { (title: String) in week.tasks.first { $0.title == title } ?? week.tasks[0] }
        #expect(TaskDestination.of(task("Complete the Words: Coral reefs")) == .player)
        #expect(try TaskDestination
            .of(task("Daily life: Campus Gym Email")) ==
            .review(attemptId: #require(task("Daily life: Campus Gym Email").lastAttemptId)))
        #expect(TaskDestination
            .of(task("Take an interview: Remote work")) == .later("Speaking tasks open in the next update of TOEFLY."))
        #expect(TaskDestination
            .of(task("Email: Missing a Meeting")) == .later("Writing tasks open in the next update of TOEFLY."))
        #expect(TaskDestination
            .of(task("Round-Up Unit 4: Past simple")) ==
            .later("Grammar and vocabulary units open in the next update of TOEFLY."))
        #expect(try TaskDestination
            .of(task("TOEFL Resources: Reading tips")) ==
            .external(#require(URL(string: "https://www.toeflresources.com/reading-section/"))))
        let other = { (kind: WeekTaskKind) in
            TaskDestination.of(WeekTask(
                id: UUID(),
                title: "x",
                section: .vocabulary,
                kind: kind,
                status: .pending,
                estimatedMinutes: 1,
            ))
        }
        #expect(other(.vocabularyReview) == .later("Vocabulary review opens in the next update of TOEFLY."))
        #expect(other(.mockExam) == .later("Mock exams open in the next update of TOEFLY."))
        #expect(other(.tutorMaterial) == .later("Materials from your tutor open in the next update of TOEFLY."))
        #expect(other(.externalResource) == .later("This link is not available."))
    }

    @Test("Routes: the player in a tab, its review in its place after the submission")
    func routes() async throws {
        let fixture = fixtureRepositories(.withPlan)
        let app = AppModel(
            configuration: .local,
            session: FakeSession(restoreResult: true),
            profiles: fixture.profiles,
            planning: fixture.planning,
            practice: fixture.practice,
            now: { testNow },
        )
        await app.start()
        #expect(app.today != nil)
        #expect(app.weeks != nil)
        let week = try await fixture.planning.week(id: weekThree)
        let cloze = try #require(week.tasks.first { $0.taskType == .completeTheWords })
        let done = try #require(week.tasks.first { $0.title == "Daily life: Campus Gym Email" })
        #expect(app.route(for: cloze) == .task(cloze))
        #expect(try app.route(for: done) == .review(attemptId: #require(done.lastAttemptId)))
        #expect(app.weekModel(weekThree) === app.weekModel(weekThree))
        app.start(cloze)
        #expect(app.selectedTab == .today)
        #expect(app.todayPath == [.task(cloze)])
        let player = try #require(app.playerModel(for: cloze))
        await player.load()
        await player.submit()
        let review = try #require(player.review)
        app.showReview(review, in: .today)
        #expect(app.todayPath == [.review(attemptId: review.attemptId)])
        #expect(app.reviewModel(review.attemptId).review == review)
        app.weeksPath = [.week(weekThree), .task(cloze)]
        app.showReview(review, in: .weeks)
        #expect(app.weeksPath == [.week(weekThree), .review(attemptId: review.attemptId)])
        #expect(app.playerModel(for: WeekTask(
            id: UUID(),
            title: "x",
            section: .reading,
            status: .pending,
            estimatedMinutes: 1,
        )) == nil)
        await app.signOut()
        #expect(app.todayPath.isEmpty)
        #expect(app.today == nil)
    }

    #if os(macOS)
        /// Runs on `swift test` (macOS) in CI; the simulator has no processes to spawn.
        @Test("The OpenAPI copies of the Swift clients match the service contracts")
        func openAPICopies() throws {
            let script = URL(filePath: #filePath)
                .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
                .deletingLastPathComponent().deletingLastPathComponent()
                .appending(path: "scripts/normalize-openapi.py")
            let process = Process()
            process.executableURL = URL(filePath: "/usr/bin/env")
            process.arguments = ["python3", script.path, "--check"]
            let output = Pipe()
            process.standardOutput = output
            try process.run()
            process.waitUntilExit()
            let text = String(bytes: output.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
            #expect(process.terminationStatus == 0, "\(text)")
        }
    #endif
}

let gregorianMinsk: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = minsk
    return calendar
}()

struct FailingPlanning: PlanningRepository {
    func currentPlan() async throws(APIError) -> StudyPlanSummary? {
        throw .offline
    }

    func currentWeek() async throws(APIError) -> WeekProgress? {
        throw .offline
    }

    func today() async throws(APIError) -> TodaySummary {
        throw .offline
    }

    func weeks() async throws(APIError) -> [WeekSummary] {
        throw .offline
    }

    func week(id _: UUID) async throws(APIError) -> Week {
        throw .offline
    }
}

struct StaticPlanning: PlanningRepository {
    let today: TodaySummary

    func currentPlan() async throws(APIError) -> StudyPlanSummary? {
        StudyPlanSummary(weeksCount: 8, currentWeekNo: 3, examDate: nil, daysToExam: nil)
    }

    func currentWeek() async throws(APIError) -> WeekProgress? {
        nil
    }

    func today() async throws(APIError) -> TodaySummary {
        today
    }

    func weeks() async throws(APIError) -> [WeekSummary] {
        []
    }

    func week(id _: UUID) async throws(APIError) -> Week {
        throw .offline
    }
}
