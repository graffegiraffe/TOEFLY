import XCTest

/// Launches the app on the in-memory fixtures (no Keycloak, no api-gateway), see `AppEnvironment`.
@MainActor
enum Launcher {
    enum Scenario: String {
        case newStudent = "new-student"
        case midOnboarding = "mid-onboarding"
        case onboarded
        case withPlan = "with-plan"
        case behind
    }

    /// Content size categories: AX3 is the largest size the layouts are checked at (phase 12 DoD).
    static let ax3 = "UICTContentSizeCategoryAccessibilityXL"

    @discardableResult
    static func launch(
        _ scenario: Scenario,
        signedIn: Bool = false,
        arguments: [String] = [],
        contentSize: String? = nil,
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-fixtures", scenario.rawValue] + (signedIn ? ["-signedIn"] : []) + arguments
        if let contentSize {
            app.launchArguments += ["-UIPreferredContentSizeCategoryName", contentSize]
        }
        app.launch()
        return app
    }
}

extension XCUIElement {
    /// Waits for the element and fails the test with its description when it does not appear.
    @discardableResult
    func waitToAppear(timeout: TimeInterval = 5, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        XCTAssertTrue(waitForExistence(timeout: timeout), "\(self) did not appear", file: file, line: line)
        return self
    }

    /// Scrolls the nearest scroll view until the element is hittable.
    func scrollIntoView(in app: XCUIApplication, maxSwipes: Int = 8) {
        var swipes = 0
        while !isHittable, swipes < maxSwipes {
            app.swipeUp()
            swipes += 1
        }
    }
}

@MainActor
extension XCTestCase {
    /// Saves a screenshot in the result bundle for the PR (light and dark, docs/12 phase 12 DoD).
    func attachScreenshot(_ app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
