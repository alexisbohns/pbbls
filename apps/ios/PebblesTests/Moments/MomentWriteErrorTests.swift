import Foundation
import Testing
@testable import Pebbles

@Suite("MomentWriteError")
struct MomentWriteErrorTests {

    @Test("finds the slug inside a PostgREST message", arguments: MomentWriteError.allCases)
    func findsEverySlug(_ error: MomentWriteError) {
        #expect(MomentWriteError.slug(in: "ERROR: \(error.rawValue) (SQLSTATE P0001)") == error)
    }

    @Test("no slug, no match")
    func noSlug() {
        #expect(MomentWriteError.slug(in: "connection reset") == nil)
        #expect(MomentWriteError.slug(in: "") == nil)
    }

    @Test("a slug maps to its own line, an unknown message to the generic one")
    func messages() {
        #expect(userMessageForMomentWriteMessage("moment_empty") == String(localized: "Add a name or a photo first."))
        #expect(userMessageForMomentWriteMessage("pebble_needs_domain") == String(localized: "Pick a part of life first."))
        #expect(userMessageForMomentWriteMessage("invalid_payload") == String(localized: "Couldn't save. Please try again."))
        #expect(userMessageForMomentWriteMessage("boom") == String(localized: "Couldn't save. Please try again."))
    }
}
