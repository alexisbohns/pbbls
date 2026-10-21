import Foundation
import Testing
@testable import Pebbles

/// #1130 D8: a press request is state a stone claims when it appears, not an
/// event that fires into nothing. And a press is a value with a sequence.
@Suite("Stone press state")
@MainActor
struct StonePressRequestsTests {
    private let first = UUID(uuidString: "00000000-0000-4000-8000-000000000001") ?? UUID()
    private let second = UUID(uuidString: "00000000-0000-4000-8000-000000000002") ?? UUID()

    @Test("a request is pending with a rising sequence; a newer one replaces it")
    func request() {
        let requests = StonePressRequests()
        #expect(requests.pending == nil)
        requests.request(first)
        #expect(requests.pending == StonePressRequests.PressRequest(momentId: first, seq: 1))
        requests.request(second)
        #expect(requests.pending == StonePressRequests.PressRequest(momentId: second, seq: 2))
    }

    @Test("claiming a stale request leaves the newer one pending")
    func staleClaim() {
        let requests = StonePressRequests()
        requests.request(first)
        let stale = requests.pending
        requests.request(first)
        if let stale { requests.claim(stale) }
        #expect(requests.pending?.seq == 2)
    }

    @Test("claiming the pending request clears it, and cancel clears it too")
    func claimAndCancel() throws {
        let requests = StonePressRequests()
        requests.request(first)
        requests.claim(try #require(requests.pending))
        #expect(requests.pending == nil)
        requests.request(second)
        requests.cancel()
        #expect(requests.pending == nil)
    }

    @Test("a press follows the last one and measures from its start")
    func press() {
        let start = Date(timeIntervalSince1970: 100)
        let one = StonePress.next(after: nil, still: false, now: start)
        let two = StonePress.next(after: one, still: true, now: start)
        #expect(one.seq == 1 && two.seq == 2 && two.still)
        #expect(one.elapsed(at: start.addingTimeInterval(0.25)) == 0.25)
    }

    @Test("a card face turns to the other")
    func face() {
        #expect(CardFace.recto.other == .verso && CardFace.verso.other == .recto)
        #expect(CardFace(rawValue: "verso") == .verso)
    }
}
