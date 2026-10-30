import Testing
@testable import Pebbles

/// A screen-open check never races a mutation-path one (#1306): the server
/// returns an unlock only to the call that inserts it, so the screen's check
/// waits while one is in flight and the mutation path keeps it to celebrate.
@Suite("Achievements check in flight")
@MainActor
struct AchievementsCheckInFlightTests {
    @Test("the in-flight count rises and falls, never below zero")
    func count() {
        let service = AchievementsService(client: SupabaseService().client)
        #expect(!service.isMutationCheckInFlight)
        service.beginMutationCheck()
        service.beginMutationCheck()
        #expect(service.isMutationCheckInFlight && service.mutationChecksInFlight == 2)
        service.endMutationCheck()
        service.endMutationCheck()
        service.endMutationCheck()
        #expect(!service.isMutationCheckInFlight && service.mutationChecksInFlight == 0)
    }

    @Test("a screen-open check while one is in flight asks nothing and returns at once")
    func skipped() async {
        let service = AchievementsService(client: SupabaseService().client)
        service.beginMutationCheck()
        let clock = ContinuousClock()
        let took = await clock.measure { await service.checkIgnoringFailure() }
        #expect(took < .milliseconds(50), "no request went out")
        service.endMutationCheck()
    }
}
