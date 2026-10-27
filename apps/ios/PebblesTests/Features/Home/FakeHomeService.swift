import Foundation
@testable import Pebbles

/// `HomeServicing` for `HomeModel`'s tests: one closure per method.
struct FakeHomeService: HomeServicing {
    struct Failure: Error {}

    var answer: @Sendable () async throws -> String? = { nil }

    func displayName() async throws -> String? {
        try await answer()
    }
}
