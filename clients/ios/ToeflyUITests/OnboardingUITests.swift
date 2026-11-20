import XCTest

/// EP-02: the six onboarding steps, saving after every step and resuming.
@MainActor
final class OnboardingUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func continueTapped(_ app: XCUIApplication) {
        let button = app.buttons["onboarding-continue"]
        button.scrollIntoView(in: app)
        button.tap()
    }

    private func step(_ app: XCUIApplication) -> String {
        app.staticTexts["onboarding-step"].waitToAppear().label
    }

    func test_US_02_fullOnboardingEndsOnHomeWithTheDiagnostic() {
        let app = Launcher.launch(.newStudent, signedIn: true)
        // AC-02.01.1: band, CEFR and the comparable score.
        XCTAssertEqual(step(app), "Step 1 of 6")
        app.buttons["choice-5.0"].waitToAppear().tap()
        XCTAssertTrue(app.staticTexts["5.0 ≈ C1 ≈ 95+"].waitForExistence(timeout: 2))
        attachScreenshot(app, named: "onboarding-goal")
        continueTapped(app)

        // AC-02.01.4: not booked yet.
        XCTAssertTrue(app.staticTexts["When is your exam?"].waitForExistence(timeout: 3))
        app.switches["exam-not-booked"].tap()
        XCTAssertTrue(app.staticTexts["We’ll build an 8-week plan."].waitForExistence(timeout: 2))
        continueTapped(app)

        // AC-02.02.1, AC-02.02.2: minutes and the 15-minute warning.
        XCTAssertEqual(step(app), "Step 3 of 6")
        app.buttons["choice-15"].waitToAppear().tap()
        XCTAssertTrue(app.staticTexts[
            "15 minutes a day may not be enough to reach your target by the exam date",
        ].waitForExistence(timeout: 2))
        app.buttons["choice-45"].tap()
        continueTapped(app)

        // AC-02.03.1, AC-02.03.2: language and the time zone from the device.
        XCTAssertEqual(step(app), "Step 4 of 6")
        app.buttons["language-ru"].waitToAppear().tap()
        XCTAssertTrue(app.buttons["language-ru"].isSelected)
        continueTapped(app)

        // Consents.
        XCTAssertEqual(step(app), "Step 5 of 6")
        XCTAssertTrue(app.switches["consent-ai"].waitToAppear().exists)
        continueTapped(app)

        // AC-02.04.1: the summary and "Continue to diagnostic".
        XCTAssertEqual(step(app), "Step 6 of 6")
        XCTAssertTrue(app.staticTexts["Not booked yet"].exists)
        XCTAssertTrue(app.staticTexts["45 min"].exists)
        attachScreenshot(app, named: "onboarding-summary")
        continueTapped(app)

        // AC-02.04.4: Home leads with the diagnostic while the plan is not built.
        app.otherElements["home-diagnostic"].waitToAppear()
    }

    func test_AC_02_01_2_perSectionTargetsShowAReadOnlyOverall() {
        let app = Launcher.launch(.newStudent, signedIn: true)
        app.switches["per-section"].waitToAppear().tap()
        XCTAssertTrue(app.staticTexts["Overall target: 4.5 ≈ B2 ≈ 84+"].waitForExistence(timeout: 2))
        XCTAssertFalse(app.buttons["choice-5.0"].exists)
    }

    func test_AC_02_04_2_returningStudentResumesAtTheFirstUnfilledStep() {
        let app = Launcher.launch(.midOnboarding, signedIn: true)
        XCTAssertEqual(step(app), "Step 3 of 6")
        app.buttons["onboarding-back"].tap()
        XCTAssertEqual(step(app), "Step 2 of 6")
        // The saved "not booked" answer is still there.
        XCTAssertTrue(app.staticTexts["We’ll build an 8-week plan."].waitForExistence(timeout: 2))
    }

    func test_AC_02_04_3_finishedOnboardingNeverOpensAgain() {
        let app = Launcher.launch(.onboarded, signedIn: true)
        app.staticTexts["home-greeting"].waitToAppear()
        XCTAssertFalse(app.staticTexts["onboarding-step"].exists)
    }
}
