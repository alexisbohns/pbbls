import Testing
@testable import Pebbles

/// The unlock moment's queue (D13): an empty response never presents, cards
/// advance one at a time, the last card ends the moment, and dismissing skips
/// the rest of the queue.
@MainActor
@Suite("AchievementNotificationService")
struct AchievementMomentTests {

    private func card(_ id: String, karma: Int = 0) -> AchievementMomentCard {
        AchievementMomentCard(id: id, title: id, description: nil, karmaGranted: karma)
    }

    @Test("an empty result set never presents")
    func emptyNeverPresents() {
        let service = AchievementNotificationService()
        service.present(cards: [])
        #expect(service.currentCard == nil)
        #expect(service.hapticTrigger == 0)
    }

    @Test("a single unlock shows one card and ends on advance")
    func singleCard() {
        let service = AchievementNotificationService()
        service.present(cards: [card("first-soul", karma: 5)])

        #expect(service.currentCard?.id == "first-soul")
        #expect(service.currentCard?.karmaGranted == 5)
        #expect(service.isShowingLastCard)
        #expect(service.hapticTrigger == 1)

        service.advance()
        #expect(service.currentCard == nil)
    }

    @Test("several unlocks chain in the order they were given")
    func chainedCards() {
        let service = AchievementNotificationService()
        service.present(cards: [card("pebble-count-1"), card("pebble-count-10"), card("first-glyph")])

        #expect(service.currentCard?.id == "pebble-count-1")
        #expect(!service.isShowingLastCard)

        service.advance()
        #expect(service.currentCard?.id == "pebble-count-10")

        service.advance()
        #expect(service.currentCard?.id == "first-glyph")
        #expect(service.isShowingLastCard)

        service.advance()
        #expect(service.currentCard == nil)
    }

    @Test("dismissing skips the rest of the queue")
    func dismissSkipsQueue() {
        let service = AchievementNotificationService()
        service.present(cards: [card("a"), card("b"), card("c")])

        service.dismiss()
        #expect(service.currentCard == nil)
        #expect(service.cards.isEmpty)
    }

    @Test("a fresh moment replaces the previous queue")
    func freshMomentReplaces() {
        let service = AchievementNotificationService()
        service.present(cards: [card("a"), card("b")])
        service.advance()
        #expect(service.currentCard?.id == "b")

        service.present(cards: [card("c")])
        #expect(service.currentCard?.id == "c")
        #expect(service.cards.count == 1)
        #expect(service.isShowingLastCard)
    }
}
