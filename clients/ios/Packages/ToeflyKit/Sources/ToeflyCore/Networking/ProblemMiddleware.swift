import Foundation
import HTTPTypes
import OpenAPIRuntime

/// Turns every 4xx/5xx response into `APIError.server` with the RFC 9457 `title` and `traceId`
/// (docs/05-api-contracts.md §3), so repositories only handle the success of each operation.
public struct ProblemMiddleware: ClientMiddleware {
    public init() {}

    public func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID _: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?),
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard response.status.code >= 400 else { return (response, responseBody) }
        var problem: Problem?
        if let responseBody {
            let data = try? await Data(collecting: responseBody, upTo: 64 * 1024)
            problem = data.flatMap { try? JSONDecoder().decode(Problem.self, from: $0) }
        }
        throw APIError.server(status: response.status.code, title: problem?.title, traceId: problem?.traceId)
    }

    private struct Problem: Decodable {
        let title: String?
        let traceId: String?
    }
}
