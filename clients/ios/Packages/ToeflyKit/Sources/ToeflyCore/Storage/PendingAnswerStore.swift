import Foundation

/// Answers not saved on the server yet (AC-06.03.1: "iOS — a file of the app"). A nil answer is a cleared one.
public protocol PendingAnswerStore: Sendable {
    func load(attemptId: UUID) -> [UUID: Answer?]
    func save(_ pending: [UUID: Answer?], attemptId: UUID)
}

private struct PendingEntry: Codable {
    var itemId: UUID
    var answer: Answer?
}

/// One JSON file per attempt in Application Support, excluded from backups.
public struct FilePendingAnswerStore: PendingAnswerStore {
    private let directory: URL

    public init(directory: URL? = nil) {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        self.directory = directory ?? base.appending(path: "pending-answers", directoryHint: .isDirectory)
    }

    private func file(_ attemptId: UUID) -> URL {
        directory.appending(path: "\(attemptId.uuidString.lowercased()).json")
    }

    public func load(attemptId: UUID) -> [UUID: Answer?] {
        guard let data = try? Data(contentsOf: file(attemptId)),
              let entries = try? JSONDecoder().decode([PendingEntry].self, from: data) else { return [:] }
        var result: [UUID: Answer?] = [:]
        for entry in entries {
            result[entry.itemId] = .some(entry.answer)
        }
        return result
    }

    public func save(_ pending: [UUID: Answer?], attemptId: UUID) {
        let url = file(attemptId)
        guard !pending.isEmpty else {
            try? FileManager.default.removeItem(at: url)
            return
        }
        let entries = pending.map { PendingEntry(itemId: $0.key, answer: $0.value) }
            .sorted { $0.itemId.uuidString < $1.itemId.uuidString }
        guard let data = try? JSONEncoder().encode(entries) else { return }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var target = url
        try? data.write(to: target, options: [.atomic, .completeFileProtection])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? target.setResourceValues(values)
    }
}

public final class InMemoryPendingAnswerStore: PendingAnswerStore, @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [UUID: [UUID: Answer?]] = [:]

    public init() {}

    public func load(attemptId: UUID) -> [UUID: Answer?] {
        lock.withLock { storage[attemptId] ?? [:] }
    }

    public func save(_ pending: [UUID: Answer?], attemptId: UUID) {
        lock.withLock { storage[attemptId] = pending.isEmpty ? nil : pending }
    }
}
