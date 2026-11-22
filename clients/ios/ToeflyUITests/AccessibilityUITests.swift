import XCTest

/// Phase 12 DoD: VoiceOver labels on every interactive element, Dynamic Type up to AX3 without clipping, and
/// screenshots of every screen in the light and dark themes for the PR.
@MainActor
final class AccessibilityUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = true
    }

    override func tearDown() async throws {
        // `.unspecified` is rejected by the simulator; the other tests expect the light theme.
        await MainActor.run { XCUIDevice.shared.appearance = .light }
    }

    /// Every button, switch and tab has a non-empty accessibility label.
    private func assertLabels(_ app: XCUIApplication, screen: String) {
        let queries = [app.buttons, app.switches, app.tabBars.buttons]
        for query in queries {
            for element in query.allElementsBoundByIndex where element.exists && element.isHittable {
                XCTAssertFalse(
                    element.label.trimmingCharacters(in: .whitespaces).isEmpty,
                    "\(screen): \(element.debugDescription)",
                )
            }
        }
    }

    /// Xcode's accessibility audit: descriptions, hit regions and clipped text. The `.dynamicType` audit is left
    /// out on purpose: the app caps text at AX3 (the DoD limit), which the audit reports as "partially unsupported";
    /// AX3 itself is checked for clipping by `test_dynamicTypeAX3WithoutClipping`.
    private func audit(_ app: XCUIApplication, screen: String) {
        let interactive: Set<XCUIElement.ElementType> = [.button, .switch, .toggle, .link, .textField, .slider]
        do {
            try app.performAccessibilityAudit(for: [.sufficientElementDescription, .hitRegion, .textClipped]) { issue in
                // System-drawn controls (the date picker grid, the tab bar) are Apple's; only TOEFLY views are judged.
                let element = issue.element
                let systemOwned = element == nil || element?.elementType == .datePicker
                    || element?.elementType == .tabBar || element?.identifier.hasPrefix("_") == true
                // Hit regions matter only for controls; progress bars and labels are not tapped.
                let notTappable = issue.auditType == .hitRegion && !interactive.contains(element?.elementType ?? .other)
                if systemOwned || notTappable {
                    return true
                }
                XCTFail("\(screen): \(issue.compactDescription) — \(element?.debugDescription ?? "no element")")
                return true
            }
        } catch {
            XCTFail("\(screen): audit failed \(error)")
        }
    }

    private func screens(appearance: XCUIDevice.Appearance, contentSize: String? = nil, check: Bool) {
        XCUIDevice.shared.appearance = appearance
        let suffix = (appearance == .dark ? "dark" : "light") + (contentSize == nil ? "" : "-ax3")

        let welcome = Launcher.launch(.newStudent, contentSize: contentSize)
        welcome.buttons["get-started"].waitToAppear()
        attachScreenshot(welcome, named: "welcome-\(suffix)")
        if check {
            assertLabels(welcome, screen: "Welcome")
            audit(welcome, screen: "Welcome")
        }

        let onboarding = Launcher.launch(.newStudent, signedIn: true, contentSize: contentSize)
        onboarding.staticTexts["onboarding-step"].waitToAppear()
        attachScreenshot(onboarding, named: "onboarding-\(suffix)")
        if check {
            assertLabels(onboarding, screen: "Onboarding")
            audit(onboarding, screen: "Onboarding")
        }

        let home = Launcher.launch(.withPlan, signedIn: true, contentSize: contentSize)
        home.staticTexts["home-greeting"].waitToAppear()
        _ = home.otherElements["home-today"].waitForExistence(timeout: 5)
        attachScreenshot(home, named: "home-\(suffix)")
        if check {
            assertLabels(home, screen: "Home")
            audit(home, screen: "Home")
        }
        home.buttons["tutor-fab"].tap()
        _ = home.staticTexts["Toefly Tutor"].waitForExistence(timeout: 3)
        attachScreenshot(home, named: "tutor-\(suffix)")

        studyScreens(contentSize: contentSize, suffix: suffix, check: check)

        let diagnostic = Launcher.launch(.onboarded, signedIn: true, contentSize: contentSize)
        diagnostic.otherElements["home-diagnostic"].waitToAppear()
        attachScreenshot(diagnostic, named: "home-diagnostic-\(suffix)")
    }

    /// Phase 12b: Today, Weeks, the week page, a section, the players and the review.
    private func studyScreens(contentSize: String?, suffix: String, check: Bool) {
        let study = Launcher.launch(.withPlan, signedIn: true, contentSize: contentSize)
        study.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        study.otherElements["today-tasks"].waitToAppear()
        capture(study, "today", suffix: suffix, run: check)
        study.tabBars.firstMatch.buttons["Weeks"].tap()
        study.buttons["week-card-3"].waitToAppear()
        capture(study, "weeks", suffix: suffix, run: check)
        study.buttons["week-card-3"].tap()
        study.staticTexts["week-title"].waitToAppear()
        capture(study, "week", suffix: suffix, run: check)
        let card = study.buttons["section-card-listening"]
        card.scrollIntoView(in: study)
        attachScreenshot(study, named: "week-sections-\(suffix)")
        card.tap()
        _ = study.navigationBars["Listening · Week 3"].waitForExistence(timeout: 5)
        capture(study, "section", suffix: suffix, run: check)
        study.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Academic talk: Biology")).firstMatch.tap()
        _ = study.staticTexts["Question 2 of 3"].waitForExistence(timeout: 5)
        capture(study, "player-listening", suffix: suffix, run: check)
        study.buttons["player-exit"].tap()

        let review = Launcher.launch(.withPlan, signedIn: true, contentSize: contentSize)
        review.tabBars.firstMatch.buttons["Weeks"].waitToAppear().tap()
        review.buttons["week-card-3"].waitToAppear().tap()
        let done = review.buttons["week-task-Daily life: Campus Gym Email"].waitToAppear()
        done.scrollIntoView(in: review)
        done.tap()
        review.staticTexts["review-score"].waitToAppear()
        capture(review, "review", suffix: suffix, run: check)

        let player = Launcher.launch(.withPlan, signedIn: true, contentSize: contentSize)
        player.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        player.buttons["today-start"].waitToAppear().tap()
        _ = player.textFields["gap-1"].waitForExistence(timeout: 5)
        capture(player, "player-cloze", suffix: suffix, run: check)
    }

    private func capture(_ app: XCUIApplication, _ name: String, suffix: String, run: Bool) {
        attachScreenshot(app, named: "\(name)-\(suffix)")
        if run {
            assertLabels(app, screen: name)
            audit(app, screen: name)
        }
    }

    func test_lightThemeLabelsAndAudit() {
        screens(appearance: .light, check: true)
    }

    func test_darkThemeLabelsAndAudit() {
        screens(appearance: .dark, check: true)
    }

    func test_dynamicTypeAX3WithoutClipping() {
        screens(appearance: .light, contentSize: Launcher.ax3, check: true)
    }
}
