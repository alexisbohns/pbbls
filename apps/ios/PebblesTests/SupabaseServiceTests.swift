import Testing
@testable import Pebbles

@Suite("SupabaseService auth state")
@MainActor
struct SupabaseServiceTests {
    @Test("Service initializes with no session and initializing true")
    func initialStateIsInitializingWithNoSession() {
        let service = SupabaseService()
        #expect(service.session == nil)
        #expect(service.isInitializing == true)
    }
}
