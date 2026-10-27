import Foundation
@testable import Pebbles

/// `AchievementsProviding` for `HomeModel`'s tests: settable answers, the calls recorded.
@MainActor
final class FakeAchievementsProvider: AchievementsProviding {
    struct Failure: Error {}

    var catalog: [AchievementRecord] = []
    var unlocks: [AchievementUnlockRecord] = []
    var claimAnswer: Result<[AchievementCheckResult], Error> = .success([])
    /// Runs when a claim is answered, so a test can change the unlocks as the server would.
    var onClaim: () -> Void = {}

    private(set) var checkCalls = 0
    private(set) var catalogCalls = 0
    private(set) var unlocksCalls = 0
    private(set) var claimCalls = 0

    func checkIgnoringFailure() async { checkCalls += 1 }

    func loadCatalog() async throws -> [AchievementRecord] {
        catalogCalls += 1
        return catalog
    }

    func loadUnlocks() async throws -> [AchievementUnlockRecord] {
        unlocksCalls += 1
        return unlocks
    }

    func claimBluePebble() async throws -> [AchievementCheckResult] {
        claimCalls += 1
        onClaim()
        return try claimAnswer.get()
    }
}
