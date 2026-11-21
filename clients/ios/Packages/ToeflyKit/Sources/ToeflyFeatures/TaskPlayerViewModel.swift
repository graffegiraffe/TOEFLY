import Foundation
import Observation
import ToeflyCore

/// Task player of a closed task in Practice mode (US-06.01, docs/07-ux-ui.md §5.9): answers with autosave (US-06.03),
/// the countdown to the ETS norm with pause, the active-time tracker with heartbeats every 15 s that stops in the
/// background (US-16.01), the audio rules of Listening and the submission that opens the review (AC-06.01.6).
@MainActor
@Observable
public final class TaskPlayerViewModel {
    public enum LoadState: Equatable {
        case loading
        case ready
        case failed(APIError)
    }

    public enum SaveState: Equatable {
        case idle, saving, saved
        /// Kept on this device until the network is back.
        case offline
    }

    public static let heartbeatSeconds = 15
    public static let debounce: Duration = .seconds(2)

    public let title: String
    public private(set) var loadState: LoadState = .loading
    public private(set) var attempt: PracticeAttempt?
    public private(set) var answers: [UUID: Answer] = [:]
    public private(set) var index = 0
    public private(set) var paused = false
    public private(set) var idle = false
    public private(set) var countedSeconds = 0
    public private(set) var saveState: SaveState = .idle
    public var timerHidden = false
    public var confirmingSubmit = false
    public private(set) var submitting = false
    /// "You're offline — we'll submit when you're back" (AC-06.03.2).
    public private(set) var submitOffline = false
    public private(set) var submitError: APIError?
    /// The review after a successful submission; the screen then opens it.
    public private(set) var review: AttemptReview?
    /// Listening: items whose audio was heard (questions appear after the audio, docs/02 EP-08).
    public private(set) var heardItems: Set<UUID> = []
    public private(set) var setHeard = false
    public private(set) var audioReplays = 0

    private let itemSetId: UUID
    private let weekTaskId: UUID?
    private let repository: any PracticeRepository
    private let store: any PendingAnswerStore
    private let now: @Sendable () -> Date
    private let sleep: @Sendable (Duration) async throws -> Void
    private let makeKey: @Sendable () -> String
    private var tracker: ActiveTimeTracker
    private var sync: AnswerSync?
    private var saveTask: Task<Void, Never>?
    private var sequence: Int64 = 0
    private var secondsToHeartbeat = TaskPlayerViewModel.heartbeatSeconds
    private var submitKey: String
    private var finished = false
    private var foreground = true
    private var secondsToRetry = 5

    public init(
        title: String,
        itemSetId: UUID,
        weekTaskId: UUID?,
        repository: any PracticeRepository,
        store: any PendingAnswerStore = InMemoryPendingAnswerStore(),
        now: @escaping @Sendable () -> Date = Date.init,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) },
        makeKey: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
    ) {
        self.title = title
        self.itemSetId = itemSetId
        self.weekTaskId = weekTaskId
        self.repository = repository
        self.store = store
        self.now = now
        self.sleep = sleep
        self.makeKey = makeKey
        submitKey = makeKey()
        tracker = ActiveTimeTracker(now: now)
    }

    // MARK: Loading

    /// Continues the open attempt of the task or starts one (AC-04.03.4, AC-06.01.1).
    public func load() async {
        guard attempt == nil else { return }
        loadState = .loading
        do {
            let attempt = try await repository.openAttempt(itemSetId: itemSetId, weekTaskId: weekTaskId)
            let sync = AnswerSync(attempt: attempt, repository: repository, store: store)
            var answers: [UUID: Answer] = [:]
            for item in attempt.items {
                answers[item.id] = item.answer
            }
            // Answers kept on this device win over the server copy (they are newer).
            for (itemId, answer) in sync.pending {
                answers[itemId] = answer
            }
            self.answers = answers
            self.sync = sync
            self.attempt = attempt
            paused = attempt.paused
            tracker = ActiveTimeTracker(now: now)
            tracker.setPaused(attempt.paused)
            tracker.setVisible(foreground)
            // A Listening set already answered was heard before (AC-04.03.4: continue from the same place).
            heardItems = Set(attempt.items.filter { answers[$0.id]?.isAnswered == true }.map(\.id))
            setHeard = !heardItems.isEmpty
            index = attempt.items.firstIndex { answers[$0.id]?.isAnswered != true } ?? 0
            saveState = sync.hasPending ? .offline : .idle
            loadState = .ready
        } catch {
            loadState = .failed(error)
        }
    }

    // MARK: Derived

    public var layout: PlayerLayout? {
        PlayerLayout.of(attempt?.taskType)
    }

    public var items: [AttemptItem] {
        attempt?.items ?? []
    }

    public var currentItem: AttemptItem? {
        items.indices.contains(index) ? items[index] : nil
    }

    public var stimulus: Stimulus {
        attempt?.stimulus ?? Stimulus()
    }

    public var typeTitle: String {
        attempt?.taskType?.label ?? title
    }

    /// "Question 2 of 5" for the players with one question per screen.
    public var questionText: String? {
        guard let layout, !layout.isSinglePage, !items.isEmpty else { return nil }
        return "Question \(index + 1) of \(items.count)"
    }

    public var normSeconds: Int {
        Int((Double(attempt?.estimatedSeconds ?? 0) * (attempt?.timingMultiplier ?? 1)).rounded())
    }

    public var elapsedSeconds: Int {
        (attempt?.activeSeconds ?? 0) + countedSeconds
    }

    /// AC-06.01.2: the countdown, then "+mm:ss" over the norm.
    public var timer: (text: String, overtime: Bool) {
        Clock.countdown(norm: normSeconds, elapsed: elapsedSeconds)
    }

    public func answer(for item: AttemptItem) -> Answer? {
        answers[item.id]
    }

    public func isAnswered(_ item: AttemptItem) -> Bool {
        answers[item.id]?.isAnswered == true
    }

    public var unansweredCount: Int {
        items.count(where: { !isAnswered($0) })
    }

    public var isLastQuestion: Bool {
        layout?.isSinglePage == true || index >= items.count - 1
    }

    /// The content is hidden while paused or idle (AC-06.01.3).
    public var isCovered: Bool {
        paused || idle
    }

    /// Listening: the questions of the current screen show after the audio (docs/02 EP-08).
    public var questionsVisible: Bool {
        switch layout {
        case .chooseResponse: currentItem.map { heardItems.contains($0.id) } ?? false
        case .setAudio: setHeard
        default: true
        }
    }

    // MARK: Answers

    public func setAnswer(_ answer: Answer?, for itemId: UUID) {
        guard !finished, !isCovered else { return }
        answers[itemId] = answer
        sync?.change(itemId, to: answer)
        tracker.input()
        scheduleSave()
    }

    /// A tap on an option of the current question; another option replaces it (AC-07.02.2).
    public func choose(_ optionKey: String) {
        guard let item = currentItem else { return }
        setAnswer(.choice([optionKey]), for: item.id)
    }

    /// Letters of one gap of Complete the Words (AC-07.01.2, AC-07.01.3).
    public func setGap(_ item: AttemptItem, text: String) {
        let clean = Cloze.sanitize(text, length: item.content.missingLetters ?? 1)
        setAnswer(clean.isEmpty ? nil : .text(clean), for: item.id)
    }

    public func gapText(_ item: AttemptItem) -> String {
        answers[item.id]?.text ?? ""
    }

    public func slots(for item: AttemptItem) -> [String?] {
        Slots.from(answers[item.id]?.fragmentIds ?? [], count: item.content.slotCount)
    }

    /// Build a Sentence: a tap on a fragment of the bank puts it into the first free slot (WCAG 2.5.7).
    public func placeChunk(_ chunkId: String, slot: Int? = nil) {
        guard let item = currentItem else { return }
        let current = slots(for: item)
        let next = slot.map { Slots.place(current, chunk: chunkId, at: $0) }
            ?? Slots.placeInFirstFree(current, chunk: chunkId)
        setAnswer(next.contains { $0 != nil } ? .sequence(next) : nil, for: item.id)
    }

    /// A tap on a placed fragment returns it to the bank.
    public func removeChunk(_ chunkId: String) {
        guard let item = currentItem else { return }
        let next = Slots.remove(slots(for: item), chunk: chunkId)
        setAnswer(next.contains { $0 != nil } ? .sequence(next) : nil, for: item.id)
    }

    public func resetSentence() {
        guard let item = currentItem else { return }
        setAnswer(nil, for: item.id)
    }

    private func scheduleSave() {
        saveTask?.cancel()
        saveState = .saving
        saveTask = Task { [weak self, sleep] in
            try? await sleep(Self.debounce)
            guard !Task.isCancelled else { return }
            await self?.save()
        }
    }

    /// Sends the pending answers now.
    public func save() async {
        guard let sync, sync.hasPending else {
            if saveState == .saving {
                saveState = .saved
            }
            return
        }
        do {
            try await sync.flush()
            saveState = .saved
        } catch {
            saveState = .offline
        }
    }

    // MARK: Navigation

    public func go(to index: Int) {
        guard items.indices.contains(index) else { return }
        self.index = index
        tracker.input()
    }

    public func next() {
        go(to: index + 1)
    }

    public func previous() {
        go(to: index - 1)
    }

    // MARK: Time

    /// Called every second by the screen: advances the timer and sends a heartbeat every 15 s.
    public func tick() async {
        guard attempt != nil, !finished else { return }
        // AC-06.03.2: an offline submission goes out by itself once the network is back.
        if submitOffline, !submitting {
            secondsToRetry -= 1
            if secondsToRetry <= 0 {
                secondsToRetry = 5
                await submit()
                return
            }
        }
        tracker.tick()
        countedSeconds = tracker.countedSeconds
        idle = tracker.isIdle
        // Paused or in the background nothing is counted, so no heartbeat is due.
        guard !paused, foreground else { return }
        secondsToHeartbeat -= 1
        if secondsToHeartbeat <= 0 {
            await heartbeat(.active)
        }
    }

    /// Any touch on the player.
    public func input() {
        tracker.input()
        idle = false
    }

    /// AC-16.01.2: the app left the foreground (`scenePhase` ≠ `.active`) or came back. Leaving stops the time at
    /// once and sends what was counted and the pending answers.
    public func setForeground(_ foreground: Bool) async {
        guard self.foreground != foreground else { return }
        self.foreground = foreground
        tracker.setVisible(foreground)
        countedSeconds = tracker.countedSeconds
        if foreground {
            tracker.input()
            idle = false
        } else if attempt != nil, !finished {
            await heartbeat(.active)
            await save()
        }
    }

    /// AC-06.01.3: the timer stops, the task is hidden, the tracker pauses.
    public func pause() async {
        guard !paused, !finished else { return }
        tracker.setPaused(true)
        paused = true
        await heartbeat(.paused)
    }

    public func resume() async {
        guard paused else { return }
        tracker.setPaused(false)
        tracker.input()
        paused = false
        idle = false
        await heartbeat(.resumed)
    }

    /// "Are you still here?" → Resume.
    public func wake() {
        tracker.input()
        idle = false
    }

    /// Audio playing counts as activity (AC-16.01.3).
    public func setAudioPlaying(_ playing: Bool) {
        tracker.setMediaActive(playing)
    }

    /// The first play of the current audio ended: its questions appear.
    public func audioFinished() {
        switch layout {
        case .chooseResponse:
            if let item = currentItem {
                heardItems.insert(item.id)
            }
        default:
            setHeard = true
        }
    }

    /// Practice allows replays; each one is counted for the review (docs/07-ux-ui.md §3.7).
    public func countReplay() {
        audioReplays += 1
    }

    /// Plays by itself only the first time (the replays are the learner's choice).
    public var autoplaysAudio: Bool {
        !questionsVisible
    }

    public var audioURL: URL? {
        layout == .chooseResponse ? currentItem?.content.audio?.url : stimulus.audio?.url
    }

    private func heartbeat(_ state: HeartbeatState) async {
        guard let attempt, !finished else { return }
        secondsToHeartbeat = Self.heartbeatSeconds
        let millis = Int64((now().timeIntervalSince1970 * 1000).rounded())
        sequence = max(sequence + 1, millis)
        let beat = Heartbeat(
            sequence: sequence,
            state: state,
            activeSecondsDelta: tracker.take(),
            currentItemId: currentItem?.id,
            audioReplays: audioReplays,
        )
        // A lost heartbeat is not repeated: the server does not credit gaps over 60 s anyway (AC-16.01.6).
        try? await repository.heartbeat(beat, attemptId: attempt.id)
    }

    // MARK: Submit and exit

    /// AC-06.01.5: unanswered questions ask for a confirmation first.
    public func requestSubmit() async {
        if unansweredCount > 0 {
            confirmingSubmit = true
        } else {
            await submit()
        }
    }

    public func submit() async {
        guard let attempt, let sync, !submitting, !finished else { return }
        confirmingSubmit = false
        submitting = true
        submitError = nil
        submitOffline = false
        saveTask?.cancel()
        try? await sync.flush()
        await heartbeat(.active)
        do {
            let review = try await repository.submit(
                attemptId: attempt.id,
                idempotencyKey: submitKey,
                finalAnswers: sync.unsyncedAnswers,
            )
            sync.forget()
            finished = true
            self.review = review
        } catch .offline {
            submitOffline = true
        } catch {
            submitError = error
        }
        submitting = false
    }

    /// "Save and exit": the attempt stays `IN_PROGRESS` and continues from the same place (AC-04.03.4).
    public func exit() async {
        guard !finished else { return }
        saveTask?.cancel()
        await heartbeat(.active)
        await save()
    }
}
