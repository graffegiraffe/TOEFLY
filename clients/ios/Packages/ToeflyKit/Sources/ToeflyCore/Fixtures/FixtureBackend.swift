import Foundation
import HTTPTypes
import OpenAPIRuntime

/// Starting state of the in-memory backend used by UI tests, previews and the `-fixtures` launch argument.
public enum FixtureScenario: String, Sendable, CaseIterable {
    /// A student who has just signed up: the onboarding starts at the goal (EP-02).
    case newStudent = "new-student"
    /// The onboarding is half done: the goal and the exam date are saved (AC-02.04.2).
    case midOnboarding = "mid-onboarding"
    /// The onboarding is done, the plan is not built yet (AC-02.04.4).
    case onboarded
    /// Week 3 of the plan with tasks for today (US-05.02).
    case withPlan = "with-plan"
    /// Week 3 with a task of yesterday still open: Today shows the Overdue group (AC-04.04.1).
    case behind
}

/// The profile-service and planning-service operations the app calls, answered from memory through the generated
/// clients, so the fixtures exercise the same decoding as the real API.
public actor FixtureBackend {
    private var profile: [String: Any]
    private var onboarding: [String: Any]
    private var consents: [String: Bool] = [:]
    private var hasPlan: Bool
    private var study: FixtureStudy
    public private(set) var savedRequests: [String] = []

    /// Heartbeats the time tracker sent, in order (AC-16.01.1).
    public var heartbeats: [(state: String, delta: Int)] {
        study.heartbeats
    }

    public init(scenario: FixtureScenario) {
        let completed = scenario != .newStudent && scenario != .midOnboarding
        profile = [
            "id": Self.userId,
            "email": "maria.petrova@example.com",
            "displayName": "Maria Petrova",
            "roles": ["STUDENT"],
            "onboardingStatus": completed ? "COMPLETED" : (scenario == .midOnboarding ? "IN_PROGRESS" : "NOT_STARTED"),
            "createdAt": "2026-11-01T09:30:00Z",
        ]
        onboarding = [
            "status": completed ? "COMPLETED" : "NOT_STARTED",
            "nextStep": "GOAL",
            "filledSteps": [String](),
            "minutesPerDay": 60,
            "preferredStudyTime": "19:00",
            "timezone": "Europe/Minsk",
            "explanationLanguage": "en",
        ]
        hasPlan = scenario == .withPlan || scenario == .behind
        study = FixtureStudy(behind: scenario == .behind)
        if scenario == .midOnboarding {
            onboarding["status"] = "IN_PROGRESS"
            onboarding["overallTarget"] = 5.0
            onboarding["examBooked"] = false
            onboarding["filledSteps"] = ["GOAL", "EXAM_DATE"]
            onboarding["nextStep"] = "SCHEDULE"
        }
        if completed {
            onboarding["overallTarget"] = 5.0
            onboarding["examBooked"] = false
            onboarding["filledSteps"] = ["GOAL", "EXAM_DATE", "SCHEDULE", "EXPLANATION_LANGUAGE"]
            onboarding.removeValue(forKey: "nextStep")
            onboarding["minutesPerDay"] = 45
        }
    }

    /// Status and JSON body of one operation.
    func respondData(operationID: String, path: String, body: Data?) -> (Int, Data?) {
        let (status, json) = respond(operationID: operationID, path: path, body: body)
        return (status, json.flatMap { try? JSONSerialization.data(withJSONObject: $0) })
    }

    private func respond(operationID: String, path fullPath: String, body: Data?) -> (Int, Any?) {
        let json = body.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any] ?? [:]
        let components = URLComponents(string: fullPath)
        let path = components?.path ?? fullPath
        let parts = path.split(separator: "/").map(String.init)
        var query: [String: String] = [:]
        for item in components?.queryItems ?? [] {
            query[item.name] = item.value
        }
        if let response = respondStudy(operationID: operationID, parts: parts, query: query, json: json) {
            return response
        }
        switch operationID {
        case "getMe":
            return (200, profile)
        case "getOnboarding":
            return (200, onboarding)
        case "saveOnboarding":
            savedRequests.append(operationID)
            return saveOnboarding(json)
        case "listConsents":
            return (200, ["items": consents.map { Self.consent($0.key, granted: $0.value) }])
        case "recordConsent":
            let type = path.split(separator: "/").last.map(String.init) ?? ""
            let granted = json["granted"] as? Bool ?? false
            consents[type] = granted
            savedRequests.append("recordConsent:\(type)=\(granted)")
            return (200, Self.consent(type, granted: granted))
        case "getStudyGoal":
            return studyGoal()
        case "getCurrentStudyPlan", "listCurrentPlanWeeks":
            guard hasPlan else { return (404, problem(404, "No study plan yet")) }
            return operationID == "getCurrentStudyPlan" ? (200, Self.plan) : (200, ["items": (1 ... 8).map(week)])
        case "getToday":
            return (200, today())
        default:
            return (404, problem(404, "No fixture for \(operationID)"))
        }
    }

    /// planning-service weeks and practice-service attempts (phase 12b).
    private func respondStudy(
        operationID: String,
        parts: [String],
        query: [String: String],
        json: [String: Any],
    ) -> (Int, Any?)? {
        // /api/v1/attempts/{attemptId}/answers/{itemId}
        let attemptId = parts.firstIndex(of: "attempts")
            .flatMap { parts.indices.contains($0 + 1) ? parts[$0 + 1] : nil }
        let itemId = parts.last ?? ""
        switch operationID {
        case "getWeek":
            guard hasPlan, parts.last == FixtureStudy.weekId else { return (404, problem(404, "Week not found")) }
            return (200, study.week())
        case "startAttempt":
            return study.startAttempt(json)
        case "listAttempts":
            return study.listAttempts(query: query)
        case "getAttempt":
            return study.attempt(attemptId ?? "")
        case "recordAnswer":
            return study.recordAnswer(attemptId: attemptId ?? "", itemId: itemId, body: json)
        case "clearAnswer":
            return study.clearAnswer(attemptId: attemptId ?? "", itemId: itemId)
        case "sendAttemptHeartbeat":
            return study.heartbeat(attemptId: attemptId ?? "", body: json)
        case "submitAttempt":
            return study.submit(attemptId: attemptId ?? "", body: json)
        case "getAttemptReview":
            return study.review(attemptId ?? "")
        default:
            return nil
        }
    }

    private static let userId = "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a2e01"

    private static var plan: [String: Any] {
        [
            "planId": "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a2e02", "userId": userId, "examDate": NSNull(),
            "weeksCount": 8, "minutesPerDay": 45, "studyDays": [1, 2, 3, 4, 5, 6, 7], "startDate": "2026-11-02",
            "currentWeekNo": 3, "createdAt": "2026-11-01T09:30:00Z", "version": 1,
        ]
    }

    private static func consent(_ type: String, granted: Bool) -> [String: Any] {
        [
            "consentType": type,
            "granted": granted,
            "policyVersion": "2026-11",
            "source": "IOS",
            "recordedAt": "2026-11-01T09:30:00Z",
        ]
    }

    private func studyGoal() -> (Int, Any?) {
        guard onboarding["status"] as? String == "COMPLETED" else { return (404, problem(404, "Not found")) }
        return (200, [
            "overallTarget": onboarding["overallTarget"] ?? 4.5,
            "examBooked": onboarding["examBooked"] ?? false,
            "minutesPerDay": onboarding["minutesPerDay"] ?? 60,
            "revision": 1,
        ])
    }

    private func week(_ number: Int) -> [String: Any] {
        let status: String = number < 3 ? "COMPLETED" : (number == 3 ? "IN_PROGRESS" : "DRAFT")
        let progress: Double = number < 3 ? (number == 1 ? 100 : 86) : (number == 3 ? study.progressPercent : 0)
        var week: [String: Any] = ["weekNo": number, "status": status, "progressPercent": progress]
        week["weekId"] = String(format: "0193a5b0-7c1e-7a10-9d2e-6f4b8c1a3%03d", number)
        let start = Calendar(identifier: .gregorian).date(
            byAdding: .day,
            value: (number - 1) * 7,
            to: LocalDateFormat.date(from: "2026-11-02", timeZone: .gmt) ?? Date(),
        ) ?? Date()
        week["startDate"] = LocalDateFormat.string(from: start, timeZone: .gmt)
        week["endDate"] = LocalDateFormat.string(from: start.addingTimeInterval(6 * 86400), timeZone: .gmt)
        week["mockExam"] = number == 4 || number == 8
        return week
    }

    private func saveOnboarding(_ request: [String: Any]) -> (Int, Any?) {
        for key in [
            "overallTarget",
            "sectionTargets",
            "examBooked",
            "examDate",
            "minutesPerDay",
            "preferredStudyTime",
            "timezone",
            "explanationLanguage",
        ] {
            if let value = request[key] {
                onboarding[key] = value
            }
        }
        if request["examBooked"] as? Bool == false {
            onboarding.removeValue(forKey: "examDate")
        }
        // A step is filled once one of its fields is saved.
        let stepFields: [(String, [String])] = [
            ("GOAL", ["overallTarget", "sectionTargets"]),
            ("EXAM_DATE", ["examBooked"]),
            ("SCHEDULE", ["minutesPerDay"]),
            ("EXPLANATION_LANGUAGE", ["explanationLanguage"]),
        ]
        var filled = Set(onboarding["filledSteps"] as? [String] ?? [])
        for (step, fields) in stepFields where fields.contains(where: { request[$0] != nil }) {
            filled.insert(step)
        }
        let order = ["GOAL", "EXAM_DATE", "SCHEDULE", "EXPLANATION_LANGUAGE"]
        onboarding["filledSteps"] = order.filter(filled.contains)
        if let next = order.first(where: { !filled.contains($0) }) {
            onboarding["nextStep"] = next
        } else {
            onboarding.removeValue(forKey: "nextStep")
        }
        if request["complete"] as? Bool == true {
            guard filled.count == order.count else { return (422, problem(422, "Fill every step first")) }
            onboarding["status"] = "COMPLETED"
            profile["onboardingStatus"] = "COMPLETED"
        } else if onboarding["status"] as? String == "NOT_STARTED" {
            onboarding["status"] = "IN_PROGRESS"
            profile["onboardingStatus"] = "IN_PROGRESS"
        }
        return (200, onboarding)
    }

    private func today() -> [String: Any] {
        guard hasPlan else {
            return [
                "date": "2026-11-19",
                "overdue": [Any](),
                "today": [Any](),
                "vocabularyReview": ["dueCount": 0],
                "waitingForTutor": false,
                "estimatedMinutes": 0,
                "doneMinutes": 0,
            ]
        }
        return study.today()
    }

    private func problem(_ status: Int, _ title: String) -> [String: Any] {
        [
            "type": "https://toefly.app/problems/not-found",
            "title": title,
            "status": status,
            "code": "not-found",
            "traceId": "fixture-trace",
        ]
    }
}

/// `ClientTransport` over `FixtureBackend`; the generated clients cannot tell it from `URLSessionTransport`.
public struct FixtureTransport: ClientTransport {
    public let backend: FixtureBackend

    public init(backend: FixtureBackend) {
        self.backend = backend
    }

    public func send(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL _: URL,
        operationID: String,
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var data: Data?
        if let body {
            data = try await Data(collecting: body, upTo: 1024 * 1024)
        }
        let (status, payload) = await backend.respondData(
            operationID: operationID,
            path: request.path ?? "",
            body: data,
        )
        var response = HTTPResponse(status: .init(code: status))
        response.headerFields[.contentType] = status >= 400 ? "application/problem+json" : "application/json"
        return (response, payload.map { HTTPBody($0) })
    }
}
