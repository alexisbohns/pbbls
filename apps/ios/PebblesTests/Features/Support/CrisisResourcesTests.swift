import Foundation
import Testing
@testable import Pebbles

/// Pins the bundled copy to the canonical file, read in place through `#filePath`.
@Suite("CrisisResources")
struct CrisisResourcesTests {
    static let canonical = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Support
        .deletingLastPathComponent() // Features
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/shared/crisis-resources.json")

    @Test("the bundled copy is byte-identical to the canonical file")
    func parity() throws {
        let canonicalData = try Data(contentsOf: Self.canonical)
        let url = try #require(Bundle.main.url(forResource: "crisis-resources", withExtension: "json"))
        #expect(try Data(contentsOf: url) == canonicalData)
    }

    @Test("decodes seven countries")
    func countries() throws {
        let decoded = try CrisisResources.decode(Data(contentsOf: Self.canonical))
        #expect(decoded.countries.count == 7)
        #expect(CrisisResources.bundled.countries.count == 7)
    }

    @Test("default country follows the region, else FR")
    func defaults() {
        let resources = CrisisResources.bundled
        #expect(resources.defaultCountry(region: "gb")?.code == "GB")
        #expect(resources.defaultCountry(region: "XX")?.code == "FR")
        #expect(resources.defaultCountry(region: nil)?.code == "FR")
    }
}
