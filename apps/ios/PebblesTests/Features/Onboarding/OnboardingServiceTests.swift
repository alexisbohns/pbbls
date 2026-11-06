import Foundation
import Supabase
import Testing
@testable import Pebbles

/// The real service over a stubbed `URLSession`: what it actually sends. The SDK retries a GET up to
/// three times by default, 60 s an attempt, and this read holds the app while it waits.
@Suite("Onboarding service")
struct OnboardingServiceTests {
    @Test func theFlagReadIsTriedOnce() async throws {
        let host = "flag-read-\(UUID().uuidString).invalid"
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [UnavailableServer.self]
        let client = SupabaseClient(
            supabaseURL: try #require(URL(string: "https://\(host)")),
            supabaseKey: "anon",
            options: SupabaseClientOptions(
                auth: .init(storage: NoStorage(), accessToken: { "token" }),
                global: .init(session: URLSession(configuration: config))
            )
        )

        await #expect(throws: (any Error).self) {
            _ = try await OnboardingService(client: client).onboardingCompleted()
        }

        #expect(UnavailableServer.requests(to: host) == 1)
    }
}

private struct NoStorage: AuthLocalStorage {
    func store(key: String, value: Data) throws {}
    func retrieve(key: String) throws -> Data? { nil }
    func remove(key: String) throws {}
}

/// Answers every request 503, the status the SDK retries, and counts them per host.
private final class UnavailableServer: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private nonisolated(unsafe) static var counts: [String: Int] = [:]

    static func requests(to host: String) -> Int { lock.withLock { counts[host, default: 0] } }

    override static func canInit(with request: URLRequest) -> Bool { true }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let url = request.url
        Self.lock.withLock { Self.counts[url?.host() ?? "", default: 0] += 1 }
        guard let url, let response = HTTPURLResponse(
            url: url, statusCode: 503, httpVersion: nil, headerFields: ["Content-Type": "application/json"]
        ) else { return }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
