import XCTest

/// EP-04 and EP-05 in 12b: the Weeks gallery, the week page, sections and Today.
@MainActor
final class WeeksUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func openWeeks(_ scenario: Launcher.Scenario = .withPlan) -> XCUIApplication {
        let app = Launcher.launch(scenario, signedIn: true)
        app.tabBars.firstMatch.buttons["Weeks"].waitToAppear().tap()
        return app
    }

    private func openWeekThree() -> XCUIApplication {
        let app = openWeeks()
        app.buttons["week-card-3"].waitToAppear().tap()
        app.staticTexts["week-title"].waitToAppear()
        return app
    }

    func test_AC_04_01_3_galleryShowsTheCurrentWeekAndFutureWeeksBuiltLater() {
        let app = openWeeks()
        let current = app.buttons["week-card-3"].waitToAppear()
        XCTAssertTrue(current.label.contains("Week 3"))
        XCTAssertTrue(current.label.contains("Current"))
        let next = app.descendants(matching: .any)["week-card-4"]
        next.scrollIntoView(in: app)
        XCTAssertTrue(next.label.contains("Mock exam"), "AC-04.09.1: weeks 4 and 8 carry the mock exam")
        XCTAssertTrue(next.label.contains("Will be built from your progress"))
        let completed = app.buttons["completed-weeks"]
        completed.scrollIntoView(in: app)
        XCTAssertTrue(completed.label.contains("Completed weeks (2)"))
    }

    func test_AC_04_02_1_weekPageShowsDatesProgressTodayTasksAndSections() {
        let app = openWeekThree()
        XCTAssertEqual(app.staticTexts["week-title"].label, "Week 3")
        XCTAssertTrue(app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Nov 16 – Nov 22, 2026")).firstMatch.exists)
        XCTAssertTrue(app.otherElements["week-today"].exists, "the Today strip")
        XCTAssertTrue(app.staticTexts["Today: Complete the Words: Coral reefs"].exists)
        let task = app.buttons["week-task-Academic passage: Urban heat islands"]
        task.scrollIntoView(in: app)
        XCTAssertTrue(task.label.contains("Reading · Fri · 6 min"), "AC-04.02.2: type, day and time")
        let card = app.buttons["section-card-reading"]
        card.scrollIntoView(in: app)
        XCTAssertTrue(card.label.contains("1 of 3 done"))
        card.tap()
        XCTAssertTrue(app.navigationBars["Reading · Week 3"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Complete the Words: Coral reefs"].exists)
    }

    func test_AC_05_01_1_todayShowsTheTasksWithTimesAndTheTotal() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        app.otherElements["today-tasks"].waitToAppear()
        XCTAssertTrue(app.staticTexts["Today's tasks · about 24 min"].exists)
        XCTAssertTrue(app.staticTexts["today-caption"].label.hasSuffix("Week 3, day 4"))
        XCTAssertTrue(app.buttons["today-task-Complete the Words: Coral reefs"].label.contains("Reading · 3 min"))
        XCTAssertFalse(app.otherElements["today-overdue"].exists)
        let vocabulary = app.descendants(matching: .any)["today-vocabulary"]
        vocabulary.scrollIntoView(in: app)
        XCTAssertTrue(vocabulary.label.contains("12 words to review"))
        XCTAssertTrue(app.buttons["today-start"].exists)
    }

    func test_AC_04_04_1_overdueTasksComeAboveToday() {
        let app = Launcher.launch(.behind, signedIn: true)
        app.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        let overdue = app.otherElements["today-overdue"].waitToAppear()
        XCTAssertTrue(app.buttons["today-task-Conversation: Library hours"].label.contains("From yesterday"))
        XCTAssertLessThan(overdue.frame.minY, app.otherElements["today-tasks"].frame.minY)
    }

    func test_AC_05_01_4_tasksThatOpenLaterSaySo() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        app.buttons["today-task-Take an interview: Remote work"].waitToAppear().tap()
        app.otherElements["task-later"].waitToAppear()
        XCTAssertTrue(app.staticTexts["Speaking tasks open in the next update of TOEFLY."].exists)
    }

    func test_AC_11_01_1_aDoneTaskOpensItsReview() {
        let app = openWeekThree()
        let task = app.buttons["week-task-Daily life: Campus Gym Email"]
        task.scrollIntoView(in: app)
        task.tap()
        let score = app.staticTexts["review-score"].waitToAppear()
        XCTAssertEqual(score.label, "1 of 2 correct")
        XCTAssertTrue(app.staticTexts["2:25 (recommended 2:00)"].exists)
        XCTAssertTrue(app.staticTexts["+0.1 to your Reading estimate"].exists)
        let missed = app.otherElements["review-question-2"]
        missed.scrollIntoView(in: app)
        XCTAssertTrue(app.staticTexts["Why B is correct"].exists, "a missed question opens with its explanation")
        XCTAssertTrue(app.staticTexts["Why not: C is wrong: no team is mentioned."].exists)
    }
}
