import Testing
@testable import Pebbles

@Suite("SupabaseService auth state")
struct SupabaseServiceTests {
    @Test("Service initializes with no session, initializing true, no error")
    func initialStateIsInitializingWithNoSession() {
        let service = SupabaseService()
        #expect(service.session == nil)
        #expect(service.isInitializing == true)
        #expect(service.authError == nil)
    }
}
