import Foundation
import Testing
@testable import ToeflyCore
@testable import ToeflyFeatures

@Suite("Player logic")
struct PlayerLogicTests {
    @Test("AC-07.01.1 gaps of Complete the Words follow the markup in order")
    func ac07011Gaps() {
        let segments = Cloze.parse("Coral reefs grow. Yet th{_} sup{_} life.")
        #expect(segments == [
            .text("Coral reefs grow. Yet "),
            .gap(index: 0, prefix: "th"),
            .text(" "),
            .gap(index: 1, prefix: "sup"),
            .text(" life."),
        ])
        #expect(Cloze.parse("No gaps.") == [.text("No gaps.")])
    }

    @Test("AC-07.01.3 a gap takes Latin letters, an apostrophe and a hyphen, up to its cells")
    func ac07013Letters() {
        #expect(Cloze.sanitize("ab1ć-'xyz", length: 5) == "ab-'x")
        #expect(Cloze.sanitize("", length: 3).isEmpty)
    }

    @Test("AC-07.03.2 the word of a vocabulary question is highlighted")
    func ac07032Highlight() {
        let pieces = Passage.pieces(
            of: "Dark roofs absorb sunlight.",
            paragraph: 0,
            highlight: PassageRange(paragraph: 0, charStart: 11, charEnd: 17),
        )
        #expect(pieces == [
            .text("Dark roofs ", highlighted: false),
            .text("absorb", highlighted: true),
            .text(" sunlight.", highlighted: false),
        ])
        #expect(Passage.pieces(
            of: "Text.",
            paragraph: 1,
            highlight: PassageRange(paragraph: 0, charStart: 0, charEnd: 2),
        )
            == [.text("Text.", highlighted: false)])
    }

    @Test("AC-07.03.3 insert markers stand after their sentences; a marker past the end goes to the end")
    func ac07033Markers() {
        let markers = [
            InsertMarker(key: "A", paragraph: 0, afterSentence: 0),
            InsertMarker(key: "B", paragraph: 0, afterSentence: 5),
            InsertMarker(key: "C", paragraph: 1, afterSentence: 0),
        ]
        let pieces = Passage.pieces(of: "One. Two!", paragraph: 0, markers: markers)
        #expect(pieces == [
            .text("One.", highlighted: false), .marker("A"), .text(" Two!", highlighted: false), .marker("B"),
        ])
        #expect(Passage.sentences("No end") == ["No end"])
    }

    @Test("AC-09.01.2 fragments move between the bank and the slots")
    func ac09012Slots() {
        let chunks = [Chunk(id: "c1", text: "have"), Chunk(id: "c2", text: "not"), Chunk(id: "c3", text: "done")]
        var slots = Slots.from([], count: 2)
        slots = Slots.placeInFirstFree(slots, chunk: "c1")
        slots = Slots.placeInFirstFree(slots, chunk: "c2")
        #expect(slots == ["c1", "c2"])
        #expect(Slots.placeInFirstFree(slots, chunk: "c3") == slots, "no free slot")
        #expect(Slots.bank(chunks, slots: slots).map(\.id) == ["c3"])
        #expect(Slots.place(slots, chunk: "c1", at: 1) == ["c2", "c1"], "a swap")
        #expect(Slots.place(slots, chunk: "c3", at: 0) == ["c3", "c2"], "the occupant returns to the bank")
        #expect(Slots.place(slots, chunk: "c1", at: 0) == slots)
        #expect(Slots.place(slots, chunk: "c1", at: 9) == slots)
        #expect(Slots.remove(slots, chunk: "c1") == [nil, "c2"])
        #expect(Slots.from(["c1", nil, "c3"], count: 2) == ["c1", nil])
    }

    @Test("AC-06.01.2 the countdown turns into overtime; AC-16.02.2 the pace is flagged over 30%")
    func ac06012Timer() {
        #expect(Clock.countdown(norm: 180, elapsed: 19) == ("02:41", false))
        #expect(Clock.countdown(norm: 180, elapsed: 222) == ("+00:42", true))
        #expect(Clock.short(192) == "3:12")
        #expect(Clock.paceDeviation(active: 261, norm: 180) == 45)
        #expect(Clock.paceDeviation(active: 100, norm: 180) == -44)
        #expect(Clock.paceDeviation(active: 200, norm: 180) == nil)
        #expect(Clock.paceDeviation(active: 0, norm: 180) == nil)
    }

    @Test("Layouts exist for the closed types only")
    func layouts() {
        #expect(PlayerLayout.of(.completeTheWords) == .cloze)
        #expect(PlayerLayout.of(.listenAcademicTalk) == .setAudio)
        #expect(PlayerLayout.of(.listenChooseResponse)?.hasAudio == true)
        #expect(PlayerLayout.of(.writeEmail) == nil)
        #expect(PlayerLayout.of(nil) == nil)
        #expect(PlayerLayout.cloze.isSinglePage)
        #expect(!PlayerLayout.passage.isSinglePage)
    }

    @Test("Answers keep the wire format of AnswerValue")
    func answersWireFormat() throws {
        let answers: [Answer] = [
            .choice(["B"]), .text("ter"), .sequence(["c1", nil]), .mapping([AnswerPair(key: "k", value: "v")]),
        ]
        let data = try JSONEncoder().encode(answers)
        #expect(try JSONDecoder().decode([Answer].self, from: data) == answers)
        #expect(String(bytes: data, encoding: .utf8)?.contains(#""fragmentIds":["c1",null]"#) == true)
        #expect(!Answer.text("  ").isAnswered)
        #expect(!Answer.sequence([nil]).isAnswered)
        #expect(Answer.mapping([AnswerPair(key: "k", value: "v")]).isAnswered)
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(Answer.self, from: Data(#"{"type":"X"}"#.utf8)) }
    }
}

@Suite("Active time")
struct ActiveTimeTests {
    @Test("AC-16.01.2 time stops at once in the background and resumes in the foreground")
    func ac16012Background() {
        let clock = TestClock()
        let tracker = ActiveTimeTracker(now: clock.closure)
        clock.advance(10)
        tracker.input()
        tracker.setVisible(false)
        clock.advance(300)
        tracker.tick()
        #expect(tracker.countedSeconds == 10)
        tracker.setVisible(true)
        tracker.input()
        clock.advance(5)
        tracker.input()
        #expect(tracker.countedSeconds == 15)
    }

    @Test("AC-16.01.3 two minutes without input are not credited and show the idle state")
    func ac16013Idle() {
        let clock = TestClock()
        let tracker = ActiveTimeTracker(now: clock.closure)
        clock.advance(30)
        tracker.input()
        clock.advance(130)
        tracker.tick()
        #expect(tracker.isIdle)
        #expect(!tracker.isCounting)
        tracker.input()
        #expect(tracker.countedSeconds == 30)
    }

    @Test("Playing audio counts without input; the pause does not count (AC-16.01.4)")
    func audioAndPause() {
        let clock = TestClock()
        let tracker = ActiveTimeTracker(now: clock.closure)
        tracker.setMediaActive(true)
        clock.advance(200)
        tracker.tick()
        #expect(tracker.countedSeconds == 200)
        tracker.setMediaActive(false)
        tracker.setPaused(true)
        clock.advance(60)
        tracker.tick()
        #expect(tracker.countedSeconds == 200)
        #expect(tracker.take() == 30, "at most 30 s per heartbeat")
        #expect(tracker.take() == 30)
        #expect(tracker.countedSeconds == 200)
    }
}

@MainActor
@Suite("Task player")
struct TaskPlayerTests {
    @Test("AC-06.01.1 opening a task starts a PRACTICE attempt and the task becomes IN_PROGRESS")
    func ac06011Start() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Complete the Words: Coral reefs")
        #expect(model.loadState == .ready)
        #expect(model.layout == .cloze)
        #expect(model.items.count == 10)
        #expect(model.items.first?.content.prefix == "th")
        #expect(model.items.first?.content.missingLetters == 2)
        #expect(model.stimulus.markup?.contains("th{_}") == true)
        #expect(model.questionText == nil)
        #expect(model.timer == ("03:00", false))
        let task = try await harness.task("Complete the Words: Coral reefs")
        #expect(task.status == .inProgress)
    }

    @Test("AC-04.03.4 an attempt in progress continues with its answers from the first unanswered question")
    func ac04034Resume() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Academic talk: Biology")
        #expect(model.layout == .setAudio)
        #expect(model.answer(for: model.items[0]) == .choice(["B"]))
        #expect(model.index == 1)
        #expect(model.setHeard, "an answered Listening set was heard before")
        #expect(model.questionsVisible)
        #expect(model.elapsedSeconds == 40)
        #expect(model.questionText == "Question 2 of 3")
    }

    @Test("AC-06.03.1 answers are saved with If-Match after the debounce; AC-07.01.2 one letter per cell")
    func ac06031Autosave() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Complete the Words: Coral reefs")
        let first = model.items[0]
        model.setGap(first, text: "ey!7x")
        #expect(model.gapText(first) == "ey")
        await model.save()
        #expect(model.saveState == .saved)
        #expect(harness.practice.recorded == 1)
        let attempt = try await harness.fixture.practice.attempt(id: #require(model.attempt?.id))
        #expect(attempt.items[0].answer == .text("ey"))
        model.setGap(first, text: "")
        await model.save()
        let cleared = try await harness.fixture.practice.attempt(id: attempt.id)
        #expect(cleared.items[0].answer == nil)
    }

    @Test("AC-06.03.1 offline answers stay on the device and go out when the network is back")
    func ac06031Offline() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        harness.practice.offline = true
        model.choose("A")
        await model.save()
        #expect(model.saveState == .offline)
        let attemptId = try #require(model.attempt?.id)
        #expect(harness.store.load(attemptId: attemptId).count == 1)
        harness.practice.offline = false
        await model.save()
        #expect(model.saveState == .saved)
        #expect(harness.store.load(attemptId: attemptId).isEmpty)
    }

    @Test("A stale version (412) is read again once")
    func staleVersion() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        harness.practice.staleOnce = true
        model.choose("B")
        await model.save()
        #expect(model.saveState == .saved)
        #expect(harness.practice.recorded == 1)
    }

    @Test("AC-16.01.1 a heartbeat every 15 s carries the active seconds")
    func ac16011Heartbeats() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        model.input()
        for _ in 0 ..< 15 {
            harness.clock.advance(1)
            model.input()
            await model.tick()
        }
        #expect(harness.practice.beats.count == 1)
        #expect(harness.practice.beats.first?.state == .active)
        #expect(harness.practice.beats.first?.activeSecondsDelta == 15)
        #expect(model.timer.text == "01:45")
        let beats = await harness.fixture.backend.heartbeats
        #expect(beats.map(\.delta) == [15])
    }

    @Test("AC-16.01.2 the timer and the tracker stop while the app is in the background")
    func ac16012Background() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        for _ in 0 ..< 5 {
            harness.clock.advance(1)
            model.input()
            await model.tick()
        }
        await model.setForeground(false)
        #expect(harness.practice.beats.last?.activeSecondsDelta == 5, "leaving sends what was counted")
        harness.clock.advance(600)
        await model.tick()
        await model.setForeground(false)
        #expect(model.countedSeconds == 5)
        await model.setForeground(true)
        harness.clock.advance(1)
        model.input()
        await model.tick()
        #expect(model.countedSeconds == 6)
    }

    @Test("AC-06.01.3 the pause hides the task, stops the timer and tells the server")
    func ac06013Pause() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        await model.pause()
        #expect(model.isCovered)
        model.choose("A")
        #expect(model.answer(for: model.items[0]) == nil, "no answers while paused")
        await harness.seconds(60, of: model)
        #expect(model.countedSeconds == 0)
        await model.resume()
        #expect(!model.isCovered)
        #expect(harness.practice.beats.map(\.state) == [.paused, .resumed])
        await model.pause()
        await model.pause()
        #expect(harness.practice.beats.count == 3)
    }

    @Test("Two minutes without input cover the task with \"Are you still here?\"")
    func idle() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        await harness.seconds(121, of: model)
        #expect(model.idle)
        #expect(model.isCovered)
        model.wake()
        #expect(!model.idle)
    }

    @Test("AC-06.01.4 questions can be visited back and forth")
    func ac06014Navigation() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        #expect(!model.isLastQuestion)
        model.next()
        #expect(model.index == 1)
        #expect(model.isLastQuestion)
        model.next()
        #expect(model.index == 1)
        model.previous()
        model.go(to: 7)
        #expect(model.index == 0)
    }

    @Test("AC-06.01.5 unanswered questions ask first; AC-06.01.6 the submission returns the review")
    func ac06015Submit() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        model.choose("A")
        await model.requestSubmit()
        #expect(model.confirmingSubmit)
        #expect(model.unansweredCount == 1)
        await model.submit()
        let review = try #require(model.review)
        #expect(review.correct == 1)
        #expect(review.total == 2)
        #expect(review.unanswered == 1)
        let task = try await harness.task("Daily life: Campus Gym Email")
        #expect(task.status == .done)
        #expect(task.lastAttemptId == review.attemptId)
        model.choose("B")
        #expect(model.answer(for: model.items[0]) == .choice(["A"]), "no changes after the submission")
    }

    @Test("AC-06.03.2 an offline submission is kept and goes out by itself")
    func ac06032OfflineSubmit() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Daily life: Campus Gym Email")
        model.choose("A")
        model.next()
        model.choose("B")
        harness.practice.offline = true
        await model.requestSubmit()
        #expect(model.submitOffline)
        #expect(model.review == nil)
        harness.practice.offline = false
        await harness.seconds(5, of: model)
        #expect(model.review?.correct == 2)
    }

    @Test("Build a Sentence: taps place and return fragments")
    func buildASentence() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Build a sentence: Practice 4")
        #expect(model.layout == .buildSentence)
        let item = try #require(model.currentItem)
        #expect(model.slots(for: item) == [nil, nil, nil])
        model.placeChunk("c2")
        model.placeChunk("c3")
        model.placeChunk("c1")
        #expect(model.answer(for: item) == .sequence(["c2", "c3", "c1"]))
        model.removeChunk("c3")
        #expect(model.slots(for: item) == ["c2", nil, "c1"])
        model.placeChunk("c4", slot: 1)
        #expect(model.slots(for: item) == ["c2", "c4", "c1"])
        model.resetSentence()
        #expect(model.answer(for: item) == nil)
        model.placeChunk("c1")
        model.removeChunk("c1")
        #expect(model.answer(for: item) == nil)
    }

    @Test("Listening: the questions appear after the audio; replays are counted")
    func listening() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Choose a response: Campus life")
        #expect(model.layout == .chooseResponse)
        #expect(!model.questionsVisible)
        #expect(model.autoplaysAudio)
        #expect(model.audioURL?.lastPathComponent == "response-1.m4a")
        model.setAudioPlaying(true)
        await harness.seconds(3, of: model)
        model.setAudioPlaying(false)
        model.audioFinished()
        #expect(model.questionsVisible)
        model.countReplay()
        model.choose("A")
        model.next()
        #expect(!model.questionsVisible, "each line is heard first")
        let set = try await harness.player("Conversation: Library hours")
        #expect(!set.questionsVisible)
        set.audioFinished()
        #expect(set.questionsVisible)
        #expect(set.audioURL?.lastPathComponent == "conversation-library.m4a")
        await model.submit()
        #expect(model.review?.audioReplays == 1, "the replays go with the heartbeats")
    }

    @Test("Save and exit sends the time and the answers")
    func saveAndExit() async throws {
        let harness = PlayerHarness()
        let model = try await harness.player("Academic passage: Urban heat islands")
        #expect(model.layout == .passage)
        #expect(model.items[1].content.targetWord?.charStart == 145)
        #expect(model.items[3].content.insertion?.markers.count == 4)
        model.choose("A")
        await model.exit()
        #expect(model.saveState == .saved)
        #expect(harness.practice.beats.count == 1)
    }

    @Test("A failed start shows the error")
    func failedStart() async {
        let harness = PlayerHarness()
        let model = TaskPlayerViewModel(
            title: "Missing",
            itemSetId: UUID(),
            weekTaskId: nil,
            repository: harness.practice,
            now: harness.clock.closure,
        )
        await model.load()
        #expect(model.loadState == .failed(.server(status: 404, title: nil, traceId: nil)))
        #expect(model.typeTitle == "Missing")
        await model.tick()
        await model.submit()
        #expect(model.review == nil)
    }
}

@MainActor
@Suite("Review")
struct ReviewTests {
    @Test("AC-11.01.1 score, time against the norm and why each option is right or wrong")
    func ac11011Review() async throws {
        let harness = PlayerHarness()
        let task = try await harness.task("Daily life: Campus Gym Email")
        let model = try ReviewViewModel(attemptId: #require(task.lastAttemptId), repository: harness.practice)
        await model.load()
        #expect(model.loadState == .loaded)
        #expect(model.title == "Read in Daily Life")
        #expect(model.scoreText == "1 of 2 correct")
        #expect(model.scoreFraction == 0.5)
        #expect(model.timeText == "2:25 (recommended 2:00)")
        #expect(model.paceText == nil)
        #expect(model.abilityText == "+0.1 to your Reading estimate")
        let questions = model.questions
        #expect(questions.count == 2)
        #expect(questions[0].correct)
        #expect(questions[0].options.first { $0.key == "A" }?.state == .correct)
        #expect(questions[0].options.first { $0.key == "B" }?.whyNot == "B is wrong: the pool stays closed.")
        #expect(!questions[1].correct)
        #expect(questions[1].options.first { $0.key == "C" }?.state == .wrongChoice)
        #expect(questions[1].options.first { $0.key == "B" }?.state == .correct)
        #expect(questions[1].explanation?.text == "The email says the pool is closed for repairs.")
        #expect(!model.isCloze)
        #expect(model.replayText == nil)
    }

    @Test("AC-11.01.4 Complete the Words shows every gap with the right spelling")
    func ac11014Cloze() async throws {
        let harness = PlayerHarness()
        let player = try await harness.player("Complete the Words: Coral reefs")
        player.setGap(player.items[0], text: "ey")
        player.setGap(player.items[1], text: "por")
        await player.submit()
        let review = try #require(player.review)
        let model = ReviewViewModel(attemptId: review.attemptId, review: review, repository: harness.practice)
        await model.load()
        #expect(model.isCloze)
        #expect(model.scoreText == "1 of 10 correct")
        #expect(model.gaps.count == 10)
        #expect(model.gaps[0] == ReviewViewModel.GapRow(
            number: 1,
            prefix: "th",
            typed: "ey",
            expected: "ey",
            correct: true,
        ))
        #expect(model.gaps[1].expected == "port")
        #expect(!model.gaps[1].correct)
        #expect(model.paragraph.contains("sup{port}"), "the scored attempt shows the full words")
        #expect(model.questions.isEmpty)
    }

    @Test("AC-11.01.5 Build a Sentence compares the sentences; AC-11.01.6 Listening shows the transcript")
    func ac11015Sentences() async throws {
        let harness = PlayerHarness()
        let task = try await harness.task("Build a sentence: Practice 3")
        let model = try ReviewViewModel(attemptId: #require(task.lastAttemptId), repository: harness.practice)
        await model.load()
        let third = try #require(model.questions.last)
        #expect(third.yourSentence == "The bus has stuck for an hour in traffic.")
        #expect(third.rightSentence == "The bus got stuck for an hour in traffic.")
        #expect(third.prompt == "Why were you late this morning?")
        let player = try await harness.player("Announcement: Biology lab")
        player.audioFinished()
        player.countReplay()
        player.countReplay()
        player.choose("A")
        await player.submit()
        let listening = try ReviewViewModel(attemptId: #require(player.review?.attemptId), repository: harness.practice)
        await listening.load()
        #expect(listening.replayText == "You replayed the audio 2 times")
        #expect(listening.transcript.first?.text.hasPrefix("Attention, biology students.") == true)
    }

    @Test("A review that cannot be loaded shows the error")
    func failed() async {
        let harness = PlayerHarness()
        let model = ReviewViewModel(attemptId: UUID(), repository: harness.practice)
        await model.load()
        #expect(model.loadState == .failed(.server(status: 404, title: nil, traceId: nil)))
        #expect(model.scoreText.isEmpty)
        #expect(model.timeText.isEmpty)
        #expect(model.scoreFraction == 0)
        #expect(model.title == "Review")
    }

    @Test("Pace and replay texts")
    func texts() {
        let review = { (active: Int, replays: Int, change: AbilityChange?) in
            AttemptReview(
                attemptId: UUID(), taskType: .listenConversation, section: .listening, correct: 1, total: 2,
                activeSeconds: active, estimatedSeconds: 100, audioReplays: replays, abilityChange: change, items: [],
            )
        }
        let repository = FlakyPractice(fixtureRepositories(.withPlan).practice)
        let slow = ReviewViewModel(attemptId: UUID(), review: review(145, 1, nil), repository: repository)
        #expect(slow.paceText == "You were 45% slower than the exam pace")
        #expect(slow.replayText == "You replayed the audio once")
        #expect(slow.abilityText == nil)
        let fast = ReviewViewModel(
            attemptId: UUID(),
            review: review(50, 0, AbilityChange(section: .listening, before: 0.2, after: 0)),
            repository: repository,
        )
        #expect(fast.paceText == "You were 50% faster than the exam pace")
        #expect(fast.abilityText == "−0.2 to your Listening estimate")
    }
}
