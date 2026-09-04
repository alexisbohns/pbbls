import Testing
@testable import Pebbles

@Suite("Visibility UI mapping")
struct VisibilityUiTests {
    @Test("every grade has a distinct SF Symbol")
    func distinctSymbols() {
        let symbols = Visibility.allCases.map(\.systemImageName)
        #expect(symbols == ["lock.fill", "person.2.fill", "globe"])
        #expect(Set(symbols).count == symbols.count)
    }
}
