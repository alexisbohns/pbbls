import Testing
@testable import Pebbles

@Suite("Pebbles smoke tests")
struct PebblesSmokeTests {
    @Test("Test target compiles and runs")
    func smokeTest() {
        #expect(1 + 1 == 2)
    }
}
