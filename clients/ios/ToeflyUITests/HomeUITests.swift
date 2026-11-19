import XCTest

/// EP-05 in 12a: Home, the five tabs and the floating AI Tutor button.
@MainActor
final class HomeUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func test_AC_05_02_1_homeShowsTheFirstThreeTasksWeekProgressAndGoal() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.otherElements["home-today"].waitToAppear()
        XCTAssertTrue(app.staticTexts["Complete the Words: Coral reefs"].exists)
        XCTAssertTrue(app.staticTexts["Academic talk: Biology"].exists)
        XCTAssertTrue(app.staticTexts["Take an interview: Remote work"].exists)
        XCTAssertFalse(app.staticTexts["Email: Missing a Meeting"].exists, "Home shows only the first three tasks")
        XCTAssertTrue(app.staticTexts["Week 3, day 4 · about 24 min"].exists)
        let week = app.otherElements["home-week"]
        week.scrollIntoView(in: app)
        XCTAssertTrue(week.exists)
        let goal = app.staticTexts["home-goal"]
        goal.scrollIntoView(in: app)
        XCTAssertEqual(goal.label, "5.0 ≈ C1 ≈ 95+")
        XCTAssertTrue(app.staticTexts["Need more data"].exists, "P6: no number without enough data")
    }

    func test_US_05_02_openTodaySwitchesToTheTodayTab() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.buttons["open-today"].waitToAppear().tap()
        app.otherElements["coming-soon-today"].waitToAppear()
    }

    func test_AC_02_04_4_withoutAPlanHomeLeadsWithTheDiagnostic() {
        let app = Launcher.launch(.onboarded, signedIn: true)
        app.otherElements["home-diagnostic"].waitToAppear()
        XCTAssertFalse(app.otherElements["home-today"].exists)
        XCTAssertTrue(app.staticTexts["Take your 25-minute diagnostic"].exists)
    }

    func test_AC_05_03_2_fiveTabsAndTheFloatingTutorButton() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.staticTexts["home-greeting"].waitToAppear()
        let tabBar = app.tabBars.firstMatch
        for title in ["Home", "Today", "Weeks", "Library", "Progress"] {
            XCTAssertTrue(tabBar.buttons[title].exists, "Tab \(title)")
        }
        for (title, id) in [("Today", "today"), ("Weeks", "weeks"), ("Library", "library"), ("Progress", "progress")] {
            tabBar.buttons[title].tap()
            app.otherElements["coming-soon-\(id)"].waitToAppear()
            XCTAssertTrue(app.buttons["tutor-fab"].isHittable, "The tutor button stays over \(title)")
        }
        tabBar.buttons["Home"].tap()
        let fab = app.buttons["tutor-fab"]
        XCTAssertEqual(fab.label, "AI tutor")
        fab.tap()
        XCTAssertTrue(app.staticTexts["Toefly Tutor"].waitForExistence(timeout: 3))
        app.buttons["tutor-close"].tap()
        XCTAssertFalse(app.staticTexts["Toefly Tutor"].waitForExistence(timeout: 1))
    }

    func test_AC_05_03_2_settingsOpenFromTheProfileOnHome() {
        let app = Launcher.launch(.onboarded, signedIn: true)
        app.buttons["profile-button"].waitToAppear().tap()
        XCTAssertTrue(app.staticTexts["maria.petrova@example.com"].waitForExistence(timeout: 3))
        app.buttons["profile-close"].tap()
        app.staticTexts["home-greeting"].waitToAppear()
    }
}
