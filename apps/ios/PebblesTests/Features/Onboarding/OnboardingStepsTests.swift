import Foundation
import Testing
@testable import Pebbles

@Suite("OnboardingSteps")
struct OnboardingStepsTests {

    @Test("contains exactly 4 steps")
    func stepCount() {
        #expect(OnboardingSteps.all.count == 4)
    }

    @Test("step IDs are unique")
    func uniqueIds() {
        let ids = OnboardingSteps.all.map(\.id)
        #expect(Set(ids).count == ids.count)
    }

    @Test("every step has a non-empty title")
    func titlesNonEmpty() {
        for step in OnboardingSteps.all {
            #expect(!String(localized: step.title).isEmpty)
        }
    }

    @Test("every step has a non-empty description")
    func descriptionsNonEmpty() {
        for step in OnboardingSteps.all {
            #expect(!String(localized: step.description).isEmpty)
        }
    }

    @Test("step IDs match the spec order")
    func idsMatchSpecOrder() {
        let ids = OnboardingSteps.all.map(\.id)
        #expect(ids == ["intro", "concept", "qualify", "carving"])
    }
}
