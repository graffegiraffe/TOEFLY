import Foundation
import Security

/// Keeps the refresh token between launches. Only the refresh token is stored; the access token stays in memory.
public protocol RefreshTokenStore: Sendable {
    func load() -> String?
    func save(_ token: String) throws
    func delete()
}

/// Keychain item `kSecClassGenericPassword`, service `app.toefly.auth`, available after the first unlock on this
/// device only and never synchronised to iCloud (docs/10-security-privacy.md §4.3).
public struct KeychainRefreshTokenStore: RefreshTokenStore {
    public static let service = "app.toefly.auth"
    private let account: String

    public init(account: String = "refresh-token") {
        self.account = account
    }

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: account,
            kSecAttrSynchronizable as String: false,
        ]
    }

    public func load() -> String? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(request as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func save(_ token: String) throws {
        let data = Data(token.utf8)
        let update: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        var status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = query
            add.merge(update) { _, new in new }
            status = SecItemAdd(add as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    public func delete() {
        SecItemDelete(query as CFDictionary)
    }
}

public struct KeychainError: Error, Equatable {
    public let status: OSStatus
}

/// Process-only store for UI tests and previews.
public final class InMemoryRefreshTokenStore: RefreshTokenStore, @unchecked Sendable {
    private let lock = NSLock()
    private var token: String?

    public init(token: String? = nil) {
        self.token = token
    }

    public func load() -> String? {
        lock.withLock { token }
    }

    public func save(_ token: String) throws {
        lock.withLock { self.token = token }
    }

    public func delete() {
        lock.withLock { token = nil }
    }
}
