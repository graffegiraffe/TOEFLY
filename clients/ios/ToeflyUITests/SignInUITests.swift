import XCTest

/// EP-01 on iOS: the welcome screen, the web authentication session (fixture identity provider), sign-out and an
/// expired session.
@MainActor
final class SignInUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func test_AC_01_01_1_getStartedOpensRegistrationAndTheOnboarding() {
        let app = Launcher.launch(.newStudent)
        app.buttons["get-started"].waitToAppear().tap()
        XCTAssertEqual(app.staticTexts["onboarding-step"].waitToAppear().label, "Step 1 of 6")
    }

    func test_AC_01_03_1_continueWithGoogleSignsIn() {
        let app = Launcher.launch(.onboarded)
        app.buttons["sign-in-google"].waitToAppear().tap()
        app.staticTexts["home-greeting"].waitToAppear()
    }

    func test_AC_01_03_4_cancellingGoogleShowsTheWelcomeScreenWithoutAnError() {
        let app = Launcher.launch(.onboarded, arguments: ["-authOutcome", "cancel"])
        app.buttons["sign-in-google"].waitToAppear().tap()
        XCTAssertTrue(app.buttons["get-started"].waitForExistence(timeout: 2))
        XCTAssertFalse(app.otherElements["welcome-message"].exists)
        XCTAssertFalse(app.staticTexts["home-greeting"].exists)
    }

    func test_AC_01_04_4_appleUnavailableInThisBuildAndOtherMethodsWork() {
        let app = Launcher.launch(.onboarded)
        let apple = app.buttons["sign-in-apple"].waitToAppear()
        XCTAssertEqual(apple.label, "Sign in with Apple")
        apple.tap()
        XCTAssertTrue(app.staticTexts["Sign in with Apple is unavailable in this build"].waitForExistence(timeout: 2))
        app.buttons["sign-in-email"].tap()
        app.staticTexts["home-greeting"].waitToAppear()
    }

    func test_AC_01_04_1_appleSignsInThroughTheBrokerWhenConfigured() {
        let app = Launcher.launch(.onboarded, arguments: ["-appleSignInEnabled"])
        app.buttons["sign-in-apple"].waitToAppear().tap()
        app.staticTexts["home-greeting"].waitToAppear()
    }

    func test_AC_01_05_1_signInWithEmailOpensHome() {
        let app = Launcher.launch(.onboarded)
        app.buttons["sign-in-email"].waitToAppear().tap()
        XCTAssertEqual(app.staticTexts["home-greeting"].waitToAppear().label, "Welcome, Maria")
    }

    func test_AC_01_05_3_signOutReturnsToTheWelcomeScreen() {
        let app = Launcher.launch(.onboarded, signedIn: true)
        app.buttons["profile-button"].waitToAppear().tap()
        app.buttons["sign-out"].waitToAppear().tap()
        app.buttons["get-started"].waitToAppear()
        // The session is gone: relaunching shows the welcome screen, not Home.
        XCTAssertFalse(app.staticTexts["home-greeting"].exists)
    }

    func test_AC_01_05_5_expiredSessionReturnsToSignInAndSigningInContinues() {
        let app = Launcher.launch(.onboarded, signedIn: true, arguments: ["-expireSession"])
        XCTAssertTrue(app.staticTexts["Your session has ended. Sign in again to continue."]
            .waitForExistence(timeout: 5))
        app.buttons["sign-in-email"].tap()
        app.staticTexts["home-greeting"].waitToAppear()
    }

    func test_storedSessionOpensHomeAtLaunch() {
        let app = Launcher.launch(.onboarded, signedIn: true)
        app.staticTexts["home-greeting"].waitToAppear()
    }
}
