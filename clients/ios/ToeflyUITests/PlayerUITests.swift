import XCTest

/// EP-06, EP-07, EP-08, EP-11 and EP-16 in 12b: the players of closed tasks, the review and the time tracker.
@MainActor
final class PlayerUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    /// Today → Start: the first open task of the day, Complete the Words.
    private func startToday() -> XCUIApplication {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        app.buttons["today-start"].waitToAppear().tap()
        app.textFields["gap-1"].waitToAppear()
        return app
    }

    private func openFromWeek(_ title: String) -> XCUIApplication {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.tabBars.firstMatch.buttons["Weeks"].waitToAppear().tap()
        app.buttons["week-card-3"].waitToAppear().tap()
        let task = app.buttons["week-task-\(title)"].waitToAppear()
        task.scrollIntoView(in: app)
        task.tap()
        return app
    }

    /// The demo: Today → Complete the Words → review with every gap right.
    func test_US_07_01_completeTheWordsFromTodayToTheReview() {
        let app = startToday()
        XCTAssertTrue(app.staticTexts["Fill in the missing letters in the paragraph."].exists)
        XCTAssertTrue(app.staticTexts["player-timer"].exists, "AC-06.01.2: the countdown is visible")
        let letters = ["ey", "port", "ter", "l", "ine", "cies", "ef", "ilt", "mals", "ether"]
        for (index, text) in letters.enumerated() {
            let gap = app.textFields["gap-\(index + 1)"]
            gap.scrollIntoView(in: app)
            gap.tap()
            gap.typeText(text)
        }
        XCTAssertEqual(app.textFields["gap-1"].value as? String, "ey")
        attachScreenshot(app, named: "player-cloze-filled")
        app.buttons["player-submit"].tap()
        let score = app.staticTexts["review-score"].waitToAppear(timeout: 10)
        XCTAssertEqual(score.label, "10 of 10 correct", "AC-06.01.6: the review opens after the submission")
        attachScreenshot(app, named: "review-cloze")
        app.buttons["review-done"].scrollIntoView(in: app)
        app.buttons["review-done"].tap()
        let task = app.buttons["today-task-Complete the Words: Coral reefs"].waitToAppear()
        XCTAssertEqual(task.value as? String, "Done", "AC-04.03.3: the task of the week is done")
    }

    func test_AC_07_01_3_aGapTakesOnlyLatinLettersUpToItsCells() {
        let app = startToday()
        let gap = app.textFields["gap-2"]
        gap.tap()
        gap.typeText("p9ö")
        XCTAssertEqual(gap.value as? String, "p")
    }

    func test_AC_16_01_2_theTimerStopsWhileTheAppIsInTheBackground() throws {
        let app = startToday()
        let timer = app.staticTexts["player-timer"]
        app.textFields["gap-1"].tap()
        let before = try XCTUnwrap(seconds(timer.value as? String))
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 8)
        app.activate()
        timer.waitToAppear()
        let after = try XCTUnwrap(seconds(timer.value as? String))
        XCTAssertLessThanOrEqual(before - after, 3, "8 s in the background did not count (\(before) → \(after))")
    }

    func test_AC_06_01_3_pauseHidesTheTaskAndStopsTheTimer() throws {
        let app = startToday()
        app.buttons["player-pause"].tap()
        app.otherElements["player-paused"].waitToAppear()
        XCTAssertTrue(app.staticTexts["The timer and your time are stopped."].exists)
        let timer = app.staticTexts["player-timer"]
        let before = try XCTUnwrap(seconds(timer.value as? String))
        Thread.sleep(forTimeInterval: 4)
        XCTAssertEqual(try XCTUnwrap(seconds(timer.value as? String)), before)
        attachScreenshot(app, named: "player-paused")
        app.buttons["player-resume"].tap()
        XCTAssertFalse(app.otherElements["player-paused"].waitForExistence(timeout: 1))
    }

    func test_AC_04_03_4_saveAndExitKeepsTheAnswers() {
        let app = startToday()
        app.textFields["gap-1"].tap()
        app.textFields["gap-1"].typeText("ey")
        app.buttons["player-exit"].tap()
        app.buttons["today-task-Complete the Words: Coral reefs"].waitToAppear().tap()
        let gap = app.textFields["gap-1"].waitToAppear()
        XCTAssertEqual(gap.value as? String, "ey")
    }

    func test_AC_06_01_5_unansweredQuestionsAskBeforeTheSubmission() {
        let app = openFromWeek("Build a sentence: Practice 4")
        app.buttons["chunk-have"].waitToAppear().tap()
        app.buttons["chunk-not"].tap()
        app.buttons["chunk-finished"].tap()
        attachScreenshot(app, named: "player-build-a-sentence")
        app.buttons["player-next"].tap()
        app.buttons["player-next"].tap()
        app.buttons["player-submit"].tap()
        XCTAssertTrue(app.alerts["Submit anyway?"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["2 questions unanswered. Unanswered questions count as wrong."].exists)
        app.alerts.buttons["Submit"].firstMatch.tap()
        XCTAssertEqual(app.staticTexts["review-score"].waitToAppear(timeout: 10).label, "1 of 3 correct")
        let missed = app.otherElements["review-question-3"]
        missed.scrollIntoView(in: app)
        XCTAssertTrue(
            app.staticTexts["The bus got stuck for an hour in traffic."].exists,
            "AC-11.01.5: the right sentence",
        )
    }

    func test_AC_07_03_3_insertTheSentenceAtAMarker() {
        let app = openFromWeek("Academic passage: Urban heat islands")
        app.buttons["option-A"].waitToAppear().tap()
        app.buttons["player-next"].tap()
        app.buttons["player-next"].tap()
        app.buttons["player-next"].tap()
        XCTAssertTrue(app.staticTexts["Where would this sentence best fit?"].waitForExistence(timeout: 3))
        app.buttons["Open the passage"].tap()
        let marker = app.buttons["marker-B"].waitToAppear()
        marker.scrollIntoView(in: app)
        marker.tap()
        XCTAssertTrue(app.staticTexts["A single large tree can cool the air like several air conditioners."].exists)
        attachScreenshot(app, named: "player-passage")
    }

    func test_EP_08_listeningQuestionsAppearAfterTheAudio() {
        let app = openFromWeek("Choose a response: Campus life")
        XCTAssertTrue(app.staticTexts["questions-after-audio"].waitToAppear().exists)
        XCTAssertFalse(app.buttons["option-A"].exists, "no options before the line is heard")
        app.buttons["option-A"].waitToAppear(timeout: 20).tap()
        attachScreenshot(app, named: "player-listening")
        XCTAssertTrue(app.buttons["audio-play"].label == "Replay")
    }

    func test_AC_04_03_4_aListeningSetInProgressContinuesAtTheNextQuestion() {
        let app = Launcher.launch(.withPlan, signedIn: true)
        app.tabBars.firstMatch.buttons["Today"].waitToAppear().tap()
        app.buttons["today-task-Academic talk: Biology"].waitToAppear().tap()
        XCTAssertTrue(app.staticTexts["Question 2 of 3"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["What is the professor’s point about mitochondria?"].exists)
    }

    /// "02:41" → 161; "+00:42" → −42.
    private func seconds(_ text: String?) -> Int? {
        guard let text else { return nil }
        let sign = text.hasPrefix("+") ? -1 : 1
        let parts = text.trimmingCharacters(in: CharacterSet(charactersIn: "+")).split(separator: ":")
            .compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return sign * (parts[0] * 60 + parts[1])
    }
}
