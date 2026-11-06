import Foundation
import Testing
@testable import Pebbles

/// Unlock cards wait for the onboarding (#1565). Every way the flow can go away must let them go,
/// or a hold left on would swallow every card in the app.
@Suite("Onboarding holds achievement cards")
@MainActor
struct OnboardingCardsHoldTests {
    private static let user = "user-1"
    private let note = OnboardingHandoffNote(userId: "user-1", reason: .goodDays)
    private let bounds = ManualBounds()
    private let service = FakeOnboardingService(completed: false)
    private let consentGate = ConsentGateModel()
    private let cards = AchievementNotificationService()
    private let presentation: OnboardingPresentation

    init() throws {
        let suite = "onboarding-cards-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        consentGate.cache = ConsentPreferences(defaults: defaults)
        consentGate.service = FakeConsentService(rows: [])
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: bounds.wait)
        presentation.gate.service = service
        presentation.bind(cards: cards)
    }

    private func card(_ id: String) -> AchievementMomentCard {
        AchievementMomentCard(id: id, title: id, description: nil, karmaGranted: 0)
    }

    private func raiseTheCover() async {
        presentation.gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await presentation.gate.work?.value
        await consentGate.work?.value
        presentation.presentCover()
        presentation.coverAppeared()
    }

    private func runOut() async {
        let watch = presentation.watch
        bounds.runOutNext()
        await watch?.value
    }

    /// The flow ends and the cover goes.
    private func endTheFlow(note: OnboardingHandoffNote?) {
        presentation.finish(note: note, consentGate: consentGate)
        presentation.coverDismissed()
    }

    private func fireTheGlyphCard() {
        cards.present(cards: [card("first-glyph")])
    }

    @Test func theCoverGoingUpHoldsTheCards() async {
        await raiseTheCover()
        fireTheGlyphCard()
        #expect(cards.currentCard == nil)
    }

    @Test func theCardsStayHeldThroughTheEndOfTheFlowWhenAComposerFollows() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: note)
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
        #expect(cards.currentCard == nil)
    }

    // 1. the composer's first save
    @Test func theComposersFirstSaveShowsThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: note)
        _ = presentation.openWaiting(parkedInvite: nil)
        #expect(presentation.handoff.take(userId: Self.user) == note)
        presentation.handoff.composerSaved()
        #expect(cards.currentCard?.id == "first-glyph")
        // The pebble made there still gets its celebration, behind the held card.
        cards.present(cards: [card("first-pebble")])
        cards.advance()
        #expect(cards.currentCard?.id == "first-pebble")
    }

    // 2. the composer closing without a save
    @Test func theComposerClosingWithoutASaveShowsThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: note)
        _ = presentation.openWaiting(parkedInvite: nil)
        _ = presentation.handoff.take(userId: Self.user)
        presentation.handoff.composerClosed()
        #expect(cards.currentCard?.id == "first-glyph")
    }

    @Test func aLaterQuickMomentChangesNothing() async {
        await raiseTheCover()
        endTheFlow(note: nil)
        _ = presentation.openWaiting(parkedInvite: nil)
        cards.present(cards: [card("a")])
        presentation.handoff.composerSaved()
        presentation.handoff.composerClosed()
        #expect(cards.currentCard?.id == "a")
        #expect(cards.cards.count == 1)
    }

    // 3. no composer
    @Test func anInviteTakingOverShowsThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: note)
        #expect(presentation.openWaiting(parkedInvite: "token") == "token")
        #expect(cards.currentCard?.id == "first-glyph")
    }

    @Test func aFlowEndingWithoutANoteShowsThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: nil)
        _ = presentation.openWaiting(parkedInvite: nil)
        #expect(cards.currentCard?.id == "first-glyph")
    }

    @Test func homeFindingNoNoteShowsThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: OnboardingHandoffNote(userId: "someone-else", reason: .goodDays))
        _ = presentation.openWaiting(parkedInvite: nil)
        #expect(cards.currentCard == nil)
        #expect(presentation.handoff.take(userId: Self.user) == nil)
        #expect(cards.currentCard?.id == "first-glyph")
    }

    // 4. the flow going away without finishing
    @Test func aSignOutShowsNothingAndLeavesNoHold() async {
        await raiseTheCover()
        fireTheGlyphCard()
        presentation.reset()
        #expect(cards.currentCard == nil)
        #expect(cards.cards.isEmpty)
        // The next account's cards are not swallowed.
        cards.present(cards: [card("next-account")])
        #expect(cards.currentCard?.id == "next-account")
    }

    @Test func aSignOutAfterTheFlowEndedBeforeTheComposerAlsoClears() async {
        await raiseTheCover()
        fireTheGlyphCard()
        endTheFlow(note: note)
        presentation.reset()
        #expect(cards.cards.isEmpty)
        cards.present(cards: [card("next-account")])
        #expect(cards.currentCard?.id == "next-account")
    }

    @Test func theCoverRecoveryGivingUpReleasesThem() async {
        presentation.gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await presentation.gate.work?.value
        await consentGate.work?.value
        presentation.presentCover()
        fireTheGlyphCard()
        #expect(cards.currentCard == nil)
        await runOut()
        await runOut()
        await runOut()
        #expect(cards.currentCard?.id == "first-glyph")
    }

    @Test func aCoverLostWhenTheFlagNoLongerWantsItReleasesThem() async {
        await raiseTheCover()
        fireTheGlyphCard()
        presentation.gate.start(userId: nil)
        presentation.coverBinding = false
        #expect(cards.currentCard?.id == "first-glyph")
    }
}
