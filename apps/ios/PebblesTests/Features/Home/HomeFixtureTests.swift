import Testing
@testable import Pebbles

@Suite("Home fixture") @MainActor
struct HomeFixtureTests {
    @Test("the lab's embedded home-wraps is the harness's, verbatim")
    func wrapsMatchTheHarness() throws {
        #expect(HomeFixture.wraps == (try HomePayloads.wraps()))
    }

    @Test func statesLoadAsNamed() async {
        let fresh = await HomeFixture.model(.newUser)
        #expect(fresh.name == "Alexis" && fresh.wraps?.months.isEmpty == true)
        #expect(fresh.checklist?.state == .inProgress && fresh.guides?.count == 3)
        let back = await HomeFixture.model(.returning)
        #expect(back.wraps?.months.count == 1 && back.checklist?.state == .done)
        let claim = await HomeFixture.model(.claim)
        #expect(claim.checklist?.state == .claimable)
    }
}
