import Testing
@testable import Pebbles

@Suite("Karma presentation decision")
struct KarmaPresentationDecisionTests {
    @Test("non-positive amount presents nothing")
    func nonPositiveIsSilent() {
        #expect(karmaPresentationDecision(amount: 0, hasDynamicIsland: true, activitiesEnabled: true) == .none)
        #expect(karmaPresentationDecision(amount: -3, hasDynamicIsland: true, activitiesEnabled: true) == .none)
    }

    @Test("Dynamic Island + activities enabled → live activity")
    func dynamicIslandPrefersLiveActivity() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: true, activitiesEnabled: true) == .liveActivity)
    }

    @Test("no Dynamic Island → capsule even when activities enabled")
    func noIslandFallsBackToCapsule() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: false, activitiesEnabled: true) == .capsule)
    }

    @Test("Dynamic Island but activities disabled → capsule")
    func islandButDisabledFallsBackToCapsule() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: true, activitiesEnabled: false) == .capsule)
    }
}
