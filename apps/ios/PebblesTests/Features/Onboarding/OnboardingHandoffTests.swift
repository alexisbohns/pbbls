import Foundation
import Testing
@testable import Pebbles

/// The one-shot mailbox between the flow and Home (#1510 D9), and the prompt it carries.
@Suite("Onboarding hand-off")
@MainActor
struct OnboardingHandoffTests {
    private let note = OnboardingHandoffNote(userId: "user-1", reason: .goodDays)

    // MARK: - The mailbox

    @Test func theSameUserTakesItOnce() {
        let mailbox = OnboardingHandoff()
        mailbox.leave(note)
        #expect(mailbox.take(userId: "user-1") == note)
        #expect(mailbox.take(userId: "user-1") == nil)
    }

    @Test func anotherUserGetsNothingAndTheNoteIsGone() {
        let mailbox = OnboardingHandoff()
        mailbox.leave(note)
        #expect(mailbox.take(userId: "user-2") == nil)
        #expect(mailbox.take(userId: "user-1") == nil)
    }

    @Test func noUserGetsNothingAndTheNoteIsGone() {
        let mailbox = OnboardingHandoff()
        mailbox.leave(note)
        #expect(mailbox.take(userId: nil) == nil)
        #expect(mailbox.take(userId: "user-1") == nil)
    }

    @Test func aNoteWithoutAUserGoesToNobody() {
        let mailbox = OnboardingHandoff()
        mailbox.leave(OnboardingHandoffNote(userId: nil, reason: .goodDays))
        #expect(mailbox.take(userId: "user-1") == nil)
    }

    @Test func anEmptyMailboxGivesNothing() {
        #expect(OnboardingHandoff().take(userId: "user-1") == nil)
    }

    @Test func leavingWakesHome() {
        let mailbox = OnboardingHandoff()
        #expect(mailbox.revision == 0)
        mailbox.leave(note)
        #expect(mailbox.revision == 1)
    }

    /// No composer follows, but Home still re-reads what the flow changed.
    @Test func droppingTheNoteStillWakesHome() {
        let mailbox = OnboardingHandoff()
        mailbox.leave(note)
        mailbox.dropNote()
        #expect(mailbox.revision == 2)
        #expect(mailbox.take(userId: "user-1") == nil)
    }

    // MARK: - Casing, end to end

    /// `Session.user.id.uuidString` is upper case. Home takes with `userKey`, the flow's note holds
    /// what the host built with `userKey`, and any other form would quietly lose the composer.
    @Test func theNoteTheFlowLeavesIsTheOneHomeTakes() async throws {
        let id = try #require(UUID(uuidString: "ABCDEF01-2345-4678-9ABC-DEF012345678"))
        let harness = OnboardingFlowHarness()
        let model = harness.model(userId: OnboardingGate.userKey(id))
        model.seedStep(.pace)
        model.next()
        await model.work?.value
        let left = try #require(model.doneNote)

        let mailbox = OnboardingHandoff()
        mailbox.leave(left)
        #expect(mailbox.take(userId: id.uuidString) == nil)

        mailbox.leave(left)
        #expect(mailbox.take(userId: OnboardingGate.userKey(id)) == left)
    }

    // MARK: - The prompt

    @Test(arguments: [
        (OnboardingReason?.some(.noticeFeelings), "What did you feel today?"),
        (.some(.keepPeople), "Who were you with today?"),
        (.some(.hardSeason), "What was hard today?"),
        (.some(.goodDays), "What was good today?"),
        (.some(.keepRecord), "What do you want to remember from today?"),
        (.some(.ownPatterns), "What happened today?"),
        (.none, "What happened today?")
    ])
    func eachReasonHasItsPrompt(reason: OnboardingReason?, english: String) {
        let prompt = OnboardingHandoffNote(userId: "u", reason: reason).prompt
        #expect(prompt.key == english)
    }

    /// Android's `values-fr`, verbatim.
    @Test(arguments: [
        (OnboardingReason?.some(.noticeFeelings), "Qu'as-tu ressenti aujourd'hui ?"),
        (.some(.keepPeople), "Avec qui étais-tu aujourd'hui ?"),
        (.some(.hardSeason), "Qu'est-ce qui a été dur aujourd'hui ?"),
        (.some(.goodDays), "Qu'est-ce qui a été bien aujourd'hui ?"),
        (.some(.keepRecord), "Que veux-tu retenir d'aujourd'hui ?"),
        (.some(.ownPatterns), "Que s'est-il passé aujourd'hui ?"),
        (.none, "Que s'est-il passé aujourd'hui ?")
    ])
    func eachPromptIsInFrench(reason: OnboardingReason?, french: String) {
        var prompt = OnboardingHandoffNote(userId: "u", reason: reason).prompt
        prompt.locale = Locale(identifier: "fr")
        #expect(String(localized: prompt) == french)
    }

    // MARK: - The composer's title

    @Test func thePromptReplacesTheMomentPagesTitle() {
        let prompt = OnboardingHandoffNote(userId: "u", reason: .hardSeason).prompt
        #expect(CaptureStep.moment.title(prompt: prompt).key == "What was hard today?")
    }

    @Test func thePromptLeavesThePhotoStepAlone() {
        let prompt = OnboardingHandoffNote(userId: "u", reason: .hardSeason).prompt
        #expect(CaptureStep.photo.title(prompt: prompt).key == CaptureStep.photo.title.key)
    }

    @Test(arguments: CaptureStep.allCases)
    func withoutAPromptEveryTitleIsItsOwn(step: CaptureStep) {
        #expect(step.title(prompt: nil).key == step.title.key)
    }
}
