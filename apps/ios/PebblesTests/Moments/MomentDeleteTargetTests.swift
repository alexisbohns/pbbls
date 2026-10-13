import Foundation
import Testing
@testable import Pebbles

@Suite("MomentDeleteTarget")
struct MomentDeleteTargetTests {

    private func summaries() throws -> [MomentSummary] {
        try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.pathMoments)
    }

    @Test("a completed moment offers both deletes, pebble first")
    func completed() throws {
        let target = MomentDeleteTarget(summary: try summaries()[0])
        #expect(target.pebbleId == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(target.options == [.pebbleKeepMoment, .moment])
        #expect(target.closesReadPage(after: .moment))
        #expect(!target.closesReadPage(after: .pebbleKeepMoment))
    }

    @Test("a bare moment offers the moment only")
    func bare() throws {
        let target = MomentDeleteTarget(summary: try summaries()[1])
        #expect(target.pebbleId == nil)
        #expect(target.options == [.moment])
    }

    @Test("built from a read-page detail the same way")
    func fromDetail() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        #expect(MomentDeleteTarget(detail: detail).options == [.pebbleKeepMoment, .moment])
    }
}
