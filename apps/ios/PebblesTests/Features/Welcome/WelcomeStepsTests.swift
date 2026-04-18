import Foundation
import Testing
@testable import Pebbles

@Suite("WelcomeSteps")
struct WelcomeStepsTests {

    @Test("contains exactly 3 steps")
    func stepCount() {
        #expect(WelcomeSteps.all.count == 3)
    }

    @Test("step IDs are unique")
    func uniqueIds() {
        let ids = WelcomeSteps.all.map(\.id)
        #expect(Set(ids).count == ids.count)
    }

    @Test("every step has a non-empty title")
    func titlesNonEmpty() {
        for step in WelcomeSteps.all {
            #expect(!step.title.isEmpty)
        }
    }

    @Test("every step has a non-empty description")
    func descriptionsNonEmpty() {
        for step in WelcomeSteps.all {
            #expect(!step.description.isEmpty)
        }
    }

    @Test("step IDs match the spec order")
    func idsMatchSpecOrder() {
        let ids = WelcomeSteps.all.map(\.id)
        #expect(ids == ["record", "enrich", "grow"])
    }
}
