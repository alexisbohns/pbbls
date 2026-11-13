import Testing
@testable import Pebbles

/// The prompt's bookkeeping, without a network: what a request arms and what a
/// cancel clears. The re-sign-in itself goes through `SupabaseService`.
@MainActor
@Suite("RecentAuthPrompt")
struct RecentAuthPromptTests {
    @Test("a request presents the alert and arms the pending action")
    func requestArms() {
        let prompt = RecentAuthPrompt()
        prompt.password = "left over"
        prompt.request { _ in }
        #expect(prompt.isPresented)
        #expect(prompt.isPending)
        #expect(prompt.password.isEmpty)
        #expect(prompt.error == nil)
        #expect(!prompt.isWorking)
    }

    @Test("cancel drops the pending action and the password")
    func cancelClears() {
        let prompt = RecentAuthPrompt()
        prompt.request { _ in }
        prompt.password = "typed"
        prompt.cancel()
        #expect(!prompt.isPending)
        #expect(prompt.password.isEmpty)
        #expect(prompt.error == nil)
    }

    @Test("a deletion starts idle, and begin only asks for confirmation")
    func deletionBegins() {
        let deletion = AccountDeletion()
        #expect(!deletion.isBusy)
        #expect(!deletion.isConfirming)
        deletion.begin()
        #expect(deletion.isConfirming)
        #expect(!deletion.isBusy)
        #expect(!deletion.recentAuth.isPending)
    }
}
