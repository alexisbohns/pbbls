import Foundation
import Testing
@testable import Pebbles

/// Spec §2 and D11: the four People states, decided by the server's totals
/// and the device's "Not now".
@Suite("People onboarding state")
struct PeopleOnboardingStateTests {
    static func prompts(pebbles: Int, souls: Int, n: Int, skipped: [String] = []) -> PeoplePrompts {
        PeoplePrompts(
            pebbles: pebbles,
            souls: souls,
            prompts: (0..<n).map { i in
                PeoplePrompts.Prompt(
                    pebbleId: UUID(), momentId: UUID(), name: "p\(i)",
                    happenedAt: Date(timeIntervalSince1970: 1_790_000_000 - Double(i) * 86_400),
                    intensity: 2, positiveness: 1, emotionId: nil
                )
            },
            skipped: skipped
        )
    }

    @Test func noPebbles() {
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 0, souls: 0, n: 0), dismissed: false) == .noPebbles)
    }

    @Test func pebblesWithoutSouls() {
        let p = Self.prompts(pebbles: 4, souls: 0, n: 3, skipped: ["x"])
        #expect(PeopleOnboardingState.make(p, dismissed: false) == .noSouls(prompts: p.prompts, skipped: ["x"]))
    }

    @Test func oneOrTwoSoulsKeepTwoPrompts() {
        let p = Self.prompts(pebbles: 9, souls: 2, n: 3)
        #expect(PeopleOnboardingState.make(p, dismissed: false) == .fewSouls(prompts: Array(p.prompts.prefix(2))))
    }

    @Test func threeSoulsOrNotNowEndIt() {
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 9, souls: 3, n: 3), dismissed: false) == .done)
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 9, souls: 1, n: 3), dismissed: true) == .done)
    }

    @Test func noSoulsWithNothingToAskStillExplains() {
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 2, souls: 0, n: 0), dismissed: false) == .noSouls(prompts: [], skipped: []))
    }
}
