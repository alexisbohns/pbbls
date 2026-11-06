import Testing
@testable import Pebbles

/// The hold (#1565): unlock cards queue while the onboarding is up and show once it is over.
@MainActor
@Suite("AchievementNotificationService hold")
struct AchievementHoldTests {
    private func card(_ id: String) -> AchievementMomentCard {
        AchievementMomentCard(id: id, title: id, description: nil, karmaGranted: 0)
    }

    @Test("a card fired while held does not show")
    func heldCardIsHidden() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("first-glyph")])
        #expect(service.currentCard == nil)
        #expect(service.cards.count == 1)
    }

    @Test("release shows the queued cards in order")
    func releaseShowsInOrder() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("a")])
        service.present(cards: [card("b"), card("c")])
        service.release()
        #expect(service.currentCard?.id == "a")
        service.advance()
        #expect(service.currentCard?.id == "b")
        service.advance()
        #expect(service.currentCard?.id == "c")
        service.advance()
        #expect(service.currentCard == nil)
    }

    @Test("the haptic waits for the release")
    func hapticWaits() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("a")])
        #expect(service.hapticTrigger == 0)
        service.release()
        #expect(service.hapticTrigger == 1)
    }

    @Test("a release with nothing queued is a no-op")
    func releaseWithNothing() {
        let service = AchievementNotificationService()
        service.release()
        service.hold()
        service.release()
        #expect(service.currentCard == nil)
        #expect(service.hapticTrigger == 0)
        // Not held any more: the next card shows at once.
        service.present(cards: [card("a")])
        #expect(service.currentCard?.id == "a")
    }

    @Test("a second release does nothing")
    func doubleRelease() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("a")])
        service.release()
        service.advance()
        service.release()
        #expect(service.currentCard == nil)
        #expect(service.hapticTrigger == 1)
    }

    @Test("a card fired after the release chains behind the held ones")
    func chainsAfterRelease() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("first-glyph")])
        service.release()
        service.present(cards: [card("first-pebble")])
        #expect(service.currentCard?.id == "first-glyph")
        service.advance()
        #expect(service.currentCard?.id == "first-pebble")
        service.advance()
        #expect(service.currentCard == nil)
        // The chain is over: a fresh moment behaves as before.
        service.present(cards: [card("x")])
        service.present(cards: [card("y")])
        #expect(service.currentCard?.id == "y")
    }

    @Test("touches are released while held, by reading currentCard")
    func touchesFreeWhileHeld() {
        let service = AchievementNotificationService()
        let capturesTouches = { service.currentCard != nil }
        service.present(cards: [card("a")])
        #expect(capturesTouches())
        service.dismiss()
        service.hold()
        service.present(cards: [card("a")])
        #expect(!capturesTouches())
        service.release()
        #expect(capturesTouches())
    }

    @Test("dismiss while held keeps the hold")
    func dismissKeepsHold() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("a")])
        service.dismiss()
        service.present(cards: [card("b")])
        #expect(service.currentCard == nil)
    }

    @Test("reset drops the queue and the hold")
    func resetClearsBoth() {
        let service = AchievementNotificationService()
        service.hold()
        service.present(cards: [card("a")])
        service.reset()
        #expect(service.cards.isEmpty)
        service.present(cards: [card("b")])
        #expect(service.currentCard?.id == "b")
    }
}
