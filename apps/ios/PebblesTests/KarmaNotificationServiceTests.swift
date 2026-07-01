import Testing
@testable import Pebbles

@MainActor
@Suite("KarmaNotificationService")
struct KarmaNotificationServiceTests {
    @Test("positive earn bumps the haptic trigger and shows the pastille")
    func positiveEarnPresents() async {
        let service = KarmaNotificationService()
        service.notifyEarned(amount: 5, reason: .pebbleCreated)
        // presentCapsule sets activeCapsule synchronously.
        #expect(service.hapticTrigger == 1)
        #expect(service.activeCapsule == KarmaEarnedContent(amount: 5, reason: .pebbleCreated))
    }

    @Test("non-positive earn is a silent no-op")
    func nonPositiveIsSilent() {
        let service = KarmaNotificationService()
        service.notifyEarned(amount: 0, reason: .pebbleCreated)
        service.notifyEarned(amount: -2, reason: .pebbleEnriched)
        #expect(service.hapticTrigger == 0)
        #expect(service.activeCapsule == nil)
    }
}
