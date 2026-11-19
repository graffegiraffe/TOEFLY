import XCTest

/// Phase 12 DoD: VoiceOver labels on every interactive element, Dynamic Type up to AX3 without clipping, and
/// screenshots of every screen in the light and dark themes for the PR.
@MainActor
final class AccessibilityUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = true
    }

    override func tearDown() async throws {
        await MainActor.run { XCUIDevice.shared.appearance = .unspecified }
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

    /// Xcode's accessibility audit: descriptions, hit regions, clipped text and Dynamic Type support.
    private func audit(_ app: XCUIApplication, screen: String) {
        do {
            try app.performAccessibilityAudit(for: [
                .sufficientElementDescription,
                .hitRegion,
                .textClipped,
                .dynamicType,
            ]) { issue in
                // System-drawn controls (the date picker grid, the tab bar) are Apple's; only TOEFLY views are judged.
                let element = issue.element
                let systemOwned = element == nil || element?.elementType == .datePicker || element?
                    .elementType == .tabBar
                    || element?.identifier.hasPrefix("_") == true
                if systemOwned {
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

        let diagnostic = Launcher.launch(.onboarded, signedIn: true, contentSize: contentSize)
        diagnostic.otherElements["home-diagnostic"].waitToAppear()
        attachScreenshot(diagnostic, named: "home-diagnostic-\(suffix)")
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
