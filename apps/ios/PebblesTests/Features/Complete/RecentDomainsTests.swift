import Foundation
import Testing
@testable import Pebbles

@Suite("RecentDomains")
struct RecentDomainsTests {
    @Test("newest first, deduplicated, five at most, per owner")
    func record() {
        let defaults = UserDefaults(suiteName: "RecentDomainsTests-\(UUID().uuidString)")!
        let owner = UUID(), other = UUID()
        let ids = (0..<7).map { _ in UUID() }
        let recent = RecentDomains(defaults: defaults)
        recent.record(Array(ids[0..<3]), owner: owner)
        recent.record([ids[2], ids[3], ids[4], ids[5], ids[6]], owner: owner)
        #expect(recent.ids(owner: owner) == [ids[2], ids[3], ids[4], ids[5], ids[6]])
        #expect(recent.ids(owner: other).isEmpty)
    }
}
