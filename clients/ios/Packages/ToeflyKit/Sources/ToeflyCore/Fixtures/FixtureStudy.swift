import Foundation

/// Week 3 of the fixture plan with its tasks and the practice attempts of planning-service and practice-service.
/// The item sets are our own texts in `Resources/ItemSets` (the format of the practice-service component test
/// fixtures, with keys and explanations); the answers are checked against those keys, like the web player stub.
/// Nothing here calls a model: explanations are the stored ones.
struct FixtureStudy {
    static let weekId = "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a3003"
    static let userId = "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a2e01"
    static let todayIndex = 4
    static let startDate = "2026-11-16"

    struct Task {
        var id: String
        var title: String
        var section: String
        var kind = "ITEM_SET"
        var taskType: String?
        var day: Int
        var ordinal: Int
        var minutes: Int
        var status = "PENDING"
        var provenance = "EXTRACTED"
        var resourceUrl: String?
        var lastAttemptId: String?
    }

    struct Attempt {
        var id: String
        var set: ItemSet
        var weekTaskId: String?
        var status = "IN_PROGRESS"
        var answers: [String: Any] = [:]
        var version = 1
        var activeSeconds = 0
        var paused = false
        var audioReplays = 0
    }

    private(set) var tasks: [Task]
    private(set) var attempts: [String: Attempt] = [:]
    private(set) var heartbeats: [(state: String, delta: Int)] = []
    private var doneMinutes = 9
    private var nextAttempt = 1

    init(behind: Bool) {
        tasks = Self.weekTasks(behind: behind)
        seedAttempts()
    }

    /// A scored attempt behind the done tasks and an open one for the task in progress (AC-04.03.4).
    private mutating func seedAttempts() {
        for (taskId, answers, scored) in [
            (tasks[6].id, [Answer.choice(["A"]), .choice(["C"])], true),
            (
                tasks[8].id,
                [Answer.sequence(["c2", "c3", "c1"]), .sequence(["c1", "c2"]), .sequence(["c4", "c1", "c3"])],
                true,
            ),
            (tasks[1].id, [Answer.choice(["B"])], false),
        ] {
            guard let index = tasks.firstIndex(where: { $0.id == taskId }),
                  let set = ItemSet.all.first(where: { $0.taskType == tasks[index].taskType }) else { continue }
            var attempt = Attempt(id: newAttemptId(), set: set, weekTaskId: taskId)
            for (offset, answer) in answers.enumerated() where offset < set.itemIds.count {
                attempt.answers[set.itemIds[offset]] = answer.json
            }
            attempt.activeSeconds = scored ? set.normSeconds + 25 : 40
            if scored {
                attempt.status = "SCORED"
                tasks[index].lastAttemptId = attempt.id
            }
            attempts[attempt.id] = attempt
        }
    }

    private mutating func newAttemptId() -> String {
        defer { nextAttempt += 1 }
        return String(format: "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a6%03d", nextAttempt)
    }

    // MARK: planning-service

    var progressPercent: Double {
        let counted = tasks.filter { $0.status != "SKIPPED" }
        let total = counted.reduce(0) { $0 + $1.minutes }
        let done = counted.filter { $0.status == "DONE" }.reduce(0) { $0 + $1.minutes }
        return total == 0 ? 0 : (Double(done) / Double(total) * 1000).rounded() / 10
    }

    func task(_ task: Task) -> [String: Any] {
        var json: [String: Any] = [
            "taskId": task.id, "weekId": Self.weekId, "kind": task.kind, "section": task.section,
            "dayIndex": task.day, "ordinal": task.ordinal, "status": task.status,
            "estimatedMinutes": task.minutes, "title": task.title, "provenance": task.provenance, "legacy": false,
        ]
        json["taskType"] = task.taskType ?? NSNull()
        json["itemSetId"] = ItemSet.all.first { $0.taskType == task.taskType }?.id ?? NSNull()
        json["lastAttemptId"] = task.lastAttemptId ?? NSNull()
        json["resourceUrl"] = task.resourceUrl ?? NSNull()
        return json
    }

    func week() -> [String: Any] {
        let sections = ["READING", "LISTENING", "WRITING", "SPEAKING", "VOCABULARY", "GRAMMAR", "RESOURCES"]
        let ordered = tasks.sorted { ($0.day, $0.ordinal) < ($1.day, $1.ordinal) }
        return [
            "weekId": Self.weekId, "planId": "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a2e02", "userId": Self.userId,
            "weekNo": 3, "status": "IN_PROGRESS", "startDate": Self.startDate, "endDate": "2026-11-22",
            "dayCount": 7, "currentDayIndex": Self.todayIndex, "progressPercent": progressPercent,
            "mockExam": false, "autoPublished": false, "pendingSlots": 0,
            "rationale": "More listening this week: two of your last four Listening sets were below 60%.",
            "sections": sections.map { section in
                ["section": section, "tasks": ordered.filter { $0.section == section }.map(task)]
            },
            "days": (1 ... 7).map { day in
                [
                    "dayIndex": day,
                    "date": String(format: "2026-11-%02d", 15 + day),
                    "estimatedMinutes": ordered.filter { $0.day == day }.reduce(0) { $0 + $1.minutes },
                    "taskIds": ordered.filter { $0.day == day }.map(\.id),
                ] as [String: Any]
            },
            "version": 3,
        ]
    }

    func today() -> [String: Any] {
        let ordered = tasks.sorted { ($0.day, $0.ordinal) < ($1.day, $1.ordinal) }
        let isOpen = { (task: Task) in task.status == "PENDING" || task.status == "IN_PROGRESS" }
        let entry = { (task: Task) -> [String: Any] in
            var entry: [String: Any] = ["task": self.task(task)]
            if task.day < Self.todayIndex {
                entry["overdueDays"] = Self.todayIndex - task.day
            }
            return entry
        }
        let overdue = ordered.filter { $0.day < Self.todayIndex && isOpen($0) }
        let today = ordered.filter { $0.day == Self.todayIndex }
        let getAhead = today
            .contains(where: isOpen) ? [] : Array(ordered.filter { $0.day > Self.todayIndex && isOpen($0) }.prefix(2))
        return [
            "date": "2026-11-19", "weekId": Self.weekId, "weekNo": 3, "dayIndex": Self.todayIndex,
            "overdue": overdue.map(entry), "today": today.map(entry), "getAhead": getAhead.map(entry),
            "vocabularyReview": ["dueCount": 12],
            "waitingForTutor": false,
            "estimatedMinutes": (overdue + today).reduce(0) { $0 + $1.minutes },
            "doneMinutes": doneMinutes,
            "goalReached": doneMinutes >= 45,
        ]
    }

    // MARK: practice-service

    mutating func startAttempt(_ request: [String: Any]) -> (Int, Any?) {
        guard let setId = request["itemSetId"] as? String,
              let set = ItemSet.all.first(where: { $0.id == setId }) else { return (404, nil) }
        let attempt = Attempt(id: newAttemptId(), set: set, weekTaskId: request["weekTaskId"] as? String)
        attempts[attempt.id] = attempt
        if let index = tasks.firstIndex(where: { $0.id == attempt.weekTaskId }), tasks[index].status == "PENDING" {
            tasks[index].status = "IN_PROGRESS"
        }
        return (201, json(attempt))
    }

    func listAttempts(query: [String: String]) -> (Int, Any?) {
        let items = attempts.values
            .filter { query["weekTaskId"] == nil || $0.weekTaskId == query["weekTaskId"] }
            .filter { query["status"] == nil || $0.status == query["status"] }
            .sorted { $0.id > $1.id }
            .map { attempt -> [String: Any] in
                [
                    "attemptId": attempt.id, "itemSetId": attempt.set.id, "taskType": attempt.set.taskType,
                    "mode": "PRACTICE", "status": attempt.status, "startedAt": "2026-11-19T09:00:00Z",
                    "weekTaskId": attempt.weekTaskId ?? NSNull(), "activeSeconds": attempt.activeSeconds,
                ]
            }
        return (200, ["items": items, "page": ["hasMore": false, "limit": 20]])
    }

    func attempt(_ id: String) -> (Int, Any?) {
        attempts[id].map { (200, json($0)) } ?? (404, nil)
    }

    mutating func recordAnswer(attemptId: String, itemId: String, body: [String: Any]) -> (Int, Any?) {
        guard var attempt = attempts[attemptId], attempt.status == "IN_PROGRESS" else { return (409, nil) }
        attempt.answers[itemId] = body["answer"]
        attempt.version += 1
        attempts[attemptId] = attempt
        return (200, [
            "itemId": itemId, "answered": true, "answer": body["answer"] ?? NSNull(),
            "attemptVersion": attempt.version, "recordedAt": "2026-11-19T09:00:00Z",
        ])
    }

    mutating func clearAnswer(attemptId: String, itemId: String) -> (Int, Any?) {
        guard var attempt = attempts[attemptId] else { return (404, nil) }
        attempt.answers.removeValue(forKey: itemId)
        attempt.version += 1
        attempts[attemptId] = attempt
        return (204, nil)
    }

    mutating func heartbeat(attemptId: String, body: [String: Any]) -> (Int, Any?) {
        guard var attempt = attempts[attemptId], attempt.status == "IN_PROGRESS" else { return (409, nil) }
        let state = body["state"] as? String ?? "ACTIVE"
        let delta = body["activeSecondsDelta"] as? Int ?? 0
        heartbeats.append((state, delta))
        attempt.activeSeconds += delta
        attempt.audioReplays = max(attempt.audioReplays, body["audioReplays"] as? Int ?? 0)
        if state == "PAUSED" {
            attempt.paused = true
        }
        if state == "RESUMED" {
            attempt.paused = false
        }
        attempts[attemptId] = attempt
        return (204, nil)
    }

    mutating func submit(attemptId: String, body: [String: Any]) -> (Int, Any?) {
        guard var attempt = attempts[attemptId] else { return (404, nil) }
        if attempt.status == "IN_PROGRESS" {
            for entry in body["finalAnswers"] as? [[String: Any]] ?? [] {
                if let itemId = entry["itemId"] as? String {
                    attempt.answers[itemId] = entry["answer"]
                }
            }
            attempt.status = "SCORED"
            attempt.version += 1
            attempts[attemptId] = attempt
            if let index = tasks.firstIndex(where: { $0.id == attempt.weekTaskId }) {
                if tasks[index].status != "DONE" {
                    doneMinutes += tasks[index].minutes
                }
                tasks[index].status = "DONE"
                tasks[index].lastAttemptId = attempt.id
            }
        }
        return (200, review(attempt))
    }

    func review(_ id: String) -> (Int, Any?) {
        guard let attempt = attempts[id] else { return (404, nil) }
        return attempt.status == "SCORED" ? (200, review(attempt)) : (409, nil)
    }
}

extension FixtureStudy {
    /// The sixteen tasks of week 3; `behind` leaves a task of yesterday open. Day 4 is today: the four tasks Home
    /// shows since phase 12a, about 24 minutes.
    static func weekTasks(behind: Bool) -> [Task] {
        // title | section | task type or kind | day (DAY: 3 when behind, else 6) | ordinal | minutes | status | origin
        let table = [
            "Complete the Words: Coral reefs|READING|COMPLETE_THE_WORDS|4|1|3|PENDING|EXTRACTED",
            "Academic talk: Biology|LISTENING|LISTEN_ACADEMIC_TALK|4|2|6|IN_PROGRESS|EXTRACTED",
            "Take an interview: Remote work|SPEAKING|TAKE_AN_INTERVIEW|4|3|5|PENDING|GENERATED",
            "Email: Missing a Meeting|WRITING|WRITE_EMAIL|4|4|10|PENDING|EXTRACTED",
            "Listen and repeat: Campus gym|SPEAKING|LISTEN_AND_REPEAT|1|1|4|DONE|EXTRACTED",
            "Collins Vocabulary Unit 3: Life Science|VOCABULARY|VOCABULARY_UNIT|1|2|30|DONE|EXTRACTED",
            "Daily life: Campus Gym Email|READING|READ_DAILY_LIFE|2|1|2|DONE|EXTRACTED",
            "Round-Up Unit 3: Present perfect|GRAMMAR|GRAMMAR_UNIT|2|2|30|DONE|EXTRACTED",
            "Build a sentence: Practice 3|WRITING|BUILD_A_SENTENCE|3|1|2|DONE|GENERATED",
            "Academic passage: Urban heat islands|READING|READ_ACADEMIC_PASSAGE|5|1|6|PENDING|EXTRACTED",
            "Build a sentence: Practice 4|WRITING|BUILD_A_SENTENCE|5|2|2|PENDING|GENERATED",
            "Conversation: Library hours|LISTENING|LISTEN_CONVERSATION|DAY|1|2|PENDING|EXTRACTED",
            "Choose a response: Campus life|LISTENING|LISTEN_CHOOSE_RESPONSE|6|2|2|PENDING|EXTRACTED",
            "TOEFL Resources: Reading tips|RESOURCES|EXTERNAL_RESOURCE|6|3|10|PENDING|EXTRACTED",
            "Announcement: Biology lab|LISTENING|LISTEN_ANNOUNCEMENT|7|1|2|PENDING|EXTRACTED",
            "Round-Up Unit 4: Past simple|GRAMMAR|GRAMMAR_UNIT|7|2|30|PENDING|EXTRACTED",
        ].map { $0.split(separator: "|").map(String.init) }
        return table.enumerated().map { index, row in
            let isTaskType = TaskType(rawValue: row[2]) != nil
            return Task(
                id: String(format: "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a4%03d", index + 1),
                title: row[0],
                section: row[1],
                kind: isTaskType ? "ITEM_SET" : row[2],
                taskType: isTaskType ? row[2] : nil,
                day: row[3] == "DAY" ? (behind ? 3 : 6) : Int(row[3]) ?? 1,
                ordinal: Int(row[4]) ?? 1,
                minutes: Int(row[5]) ?? 1,
                status: row[6],
                provenance: row[7],
                resourceUrl: row[2] == "EXTERNAL_RESOURCE" ? "https://www.toeflresources.com/reading-section/" : nil,
            )
        }
    }
}

extension FixtureStudy {
    // MARK: JSON

    private func json(_ attempt: Attempt) -> [String: Any] {
        let scored = attempt.status == "SCORED"
        return [
            "attemptId": attempt.id, "userId": Self.userId, "itemSetId": attempt.set.id,
            "taskType": attempt.set.taskType, "section": attempt.set.section ?? NSNull(), "mode": "PRACTICE",
            "status": attempt.status, "weekTaskId": attempt.weekTaskId ?? NSNull(),
            "startedAt": "2026-11-19T09:00:00Z", "estimatedSeconds": attempt.set.normSeconds,
            "activeSeconds": attempt.activeSeconds, "paused": attempt.paused, "timingMultiplier": 1.0,
            "stimulus": scored ? attempt.set.stimulus : ItemSet.redact(attempt.set.stimulus),
            "items": attempt.set.items.enumerated().map { index, item -> [String: Any] in
                let itemId = attempt.set.itemIds[index]
                return [
                    "itemId": itemId, "ordinal": item["ordinal"] ?? index + 1,
                    "content": attempt.set.visibleContent(item),
                    "answer": attempt.answers[itemId] ?? NSNull(),
                ]
            },
            "version": attempt.version,
        ]
    }

    private func review(_ attempt: Attempt) -> [String: Any] {
        let set = attempt.set
        let items = set.items.enumerated().map { index, item -> [String: Any] in
            let itemId = set.itemIds[index]
            let user = attempt.answers[itemId]
            let key = set.answers[index]
            let rightKeys = Set((key["optionIds"] as? [String]) ?? [])
            let options = item["options"] as? [[String: Any]] ?? []
            return [
                "itemId": itemId, "ordinal": item["ordinal"] ?? index + 1,
                "correct": ItemSet.normalize(user) == ItemSet.normalize(key),
                "userAnswer": user ?? NSNull(), "correctAnswer": key,
                "explanation": [
                    "text": (item["explanation"] as? [String: Any])?["summary"] as? String ?? "", "source": "STATIC",
                ],
                "distractorExplanations": options.compactMap { option -> [String: Any]? in
                    guard let optionKey = option["key"] as? String, !rightKeys.contains(optionKey),
                          let text = option["rationale"] as? String else { return nil }
                    return ["optionId": optionKey, "text": text, "source": "STATIC"]
                },
            ]
        }
        let correct = items.filter { $0["correct"] as? Bool == true }.count
        var review: [String: Any] = [
            "attemptId": attempt.id, "itemSetId": set.id, "taskType": set.taskType,
            "section": set.section ?? NSNull(), "mode": "PRACTICE", "status": "SCORED",
            "score": [
                "correct": correct, "total": items.count,
                "unanswered": set.itemIds.filter { attempt.answers[$0] == nil }.count,
            ],
            "activeSeconds": attempt.activeSeconds, "estimatedSeconds": set.normSeconds,
            "submittedAt": "2026-11-19T09:10:00Z", "scoredAt": "2026-11-19T09:10:00Z",
            "explanationLanguage": "en", "explanationPending": false, "audioReplays": attempt.audioReplays,
            "items": items, "version": attempt.version,
        ]
        if let section = set.section {
            review["abilityChange"] = [
                "section": section, "thetaBefore": -0.4, "thetaAfter": correct * 2 >= items.count ? -0.3 : -0.45,
                "standardError": 0.6,
            ]
        }
        return review
    }
}

/// An item set of `Resources/ItemSets` with its keys.
struct ItemSet: @unchecked Sendable {
    let id: String
    let taskType: String
    let section: String?
    let normSeconds: Int
    let stimulus: [String: Any]
    let items: [[String: Any]]
    let answers: [[String: Any]]
    let itemIds: [String]

    static let all: [ItemSet] = TaskType.allCases.compactMap { load($0.rawValue) }

    static func load(_ taskType: String) -> ItemSet? {
        guard let url = Bundle.module.url(forResource: taskType, withExtension: "json", subdirectory: "ItemSets"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let payload = json["payload"] as? [String: Any],
              let id = json["id"] as? String else { return nil }
        let items = payload["items"] as? [[String: Any]] ?? []
        return ItemSet(
            id: id,
            taskType: taskType,
            section: json["section"] as? String,
            normSeconds: json["etsNormSeconds"] as? Int ?? 60,
            stimulus: withAudio(payload["stimulus"]) as? [String: Any] ?? [:],
            items: items.map { withAudio($0) as? [String: Any] ?? $0 },
            answers: json["_answers"] as? [[String: Any]] ?? [],
            itemIds: items.indices.map { "\(id.prefix(24))\(String(format: "%012d", $0 + 1))" },
        )
    }

    /// Keys, explanations and transcripts stay on the server until the attempt is scored.
    static let secret: Set = [
        "answerKey", "acceptedAnswers", "acceptedKeys", "explanation", "rationale", "transcript", "fullWord",
        "suffix", "distractorChunkId",
    ]

    static func redact(_ value: Any) -> Any {
        if let array = value as? [Any] {
            return array.map(redact)
        }
        guard let object = value as? [String: Any] else { return value }
        var result: [String: Any] = [:]
        for (key, inner) in object where !secret.contains(key) {
            if key == "markup", let markup = inner as? String {
                result[key] = markup.replacingOccurrences(of: #"\{[^}]*\}"#, with: "{_}", options: .regularExpression)
            } else {
                result[key] = redact(inner)
            }
        }
        return result
    }

    func visibleContent(_ item: [String: Any]) -> Any {
        var content = Self.redact(item) as? [String: Any] ?? [:]
        if taskType == "COMPLETE_THE_WORDS", let suffix = item["suffix"] as? String {
            content["missingLetters"] = suffix.count
        }
        // The assembled sentence is the key of Build a Sentence (the sentence of an insertion is not).
        if taskType == "BUILD_A_SENTENCE" {
            content.removeValue(forKey: "sentence")
        }
        return content
    }

    /// `bundle:<file>` audio points to a clip in `Resources/Audio`.
    private static func withAudio(_ value: Any?) -> Any? {
        if let array = value as? [Any] {
            return array.map { withAudio($0) ?? $0 }
        }
        guard var object = value as? [String: Any] else { return value }
        for (key, inner) in object {
            if key == "url", let url = inner as? String, url.hasPrefix("bundle:") {
                let file = String(url.dropFirst("bundle:".count))
                let name = (file as NSString).deletingPathExtension
                let ext = (file as NSString).pathExtension
                object[key] = Bundle.module.url(forResource: name, withExtension: ext, subdirectory: "Audio")?
                    .absoluteString ?? url
            } else {
                object[key] = withAudio(inner)
            }
        }
        return object
    }

    static func normalize(_ answer: Any?) -> String {
        guard let answer = answer as? [String: Any] else { return "null" }
        switch answer["type"] as? String {
        case "TEXT":
            return "TEXT:" + (answer["text"] as? String ?? "").trimmingCharacters(in: .whitespaces).lowercased()
        case "CHOICE":
            return "CHOICE:" + ((answer["optionIds"] as? [String]) ?? []).sorted().joined(separator: ",")
        case "SEQUENCE":
            return "SEQUENCE:" + ((answer["fragmentIds"] as? [Any]) ?? []).map { "\($0)" }.joined(separator: ",")
        default:
            return "\(answer)"
        }
    }
}

extension Answer {
    /// The JSON object of the wire format.
    var json: [String: Any] {
        guard let data = try? JSONEncoder().encode(self),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return object
    }
}
