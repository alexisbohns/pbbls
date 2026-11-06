import Testing
@testable import Pebbles

@Suite("OnboardingFlowSteps")
struct OnboardingFlowStepsTests {
    @Test func sevenStepsInOrder() {
        #expect(OnboardingFlowSteps.all.map(\.id) == [.promise, .consent, .reasons, .you, .people, .pace, .handoff])
        #expect(OnboardingFlowSteps.all.map(\.skip) == [.none, .none, .skip, .skip, .later, .skip, .none])
    }

    @Test func visibleIsEverythingButTheHandoff() {
        #expect(OnboardingFlowSteps.visible.map(\.id) == [.promise, .consent, .reasons, .you, .people, .pace])
    }
}
