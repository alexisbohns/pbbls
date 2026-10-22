import Foundation
import Testing
@testable import Pebbles

/// #1146 §Souls: the card names up to eight souls in full; past eight, the
/// Spine's counting rule. Callers without a week pass `order: []`, which keeps
/// the pebble's own order (stack D's `PeopleLines`).
@Suite("The card's people line")
struct PeopleLineCardTests {
    private func ids(_ n: Int) -> [String] { (0..<n).map { "soul-\($0)" } }

    @Test("eight souls are all named, in the pebble's order when there is no week")
    func eight() {
        let line = PeopleLines.peopleLine(ids(8), order: [], locale: Vectors.en, namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ids(8))
        #expect(line.others == 0 && line.marks.isEmpty && line.overflow == 0)
    }

    @Test("nine fall back to two named, seven others in words, five marks and +2")
    func nine() {
        let line = PeopleLines.peopleLine(ids(9), order: [], locale: Vectors.en, namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ["soul-0", "soul-1"])
        #expect(line.others == 7 && line.othersWords == "seven")
        #expect(line.marks.count == 5 && line.overflow == 2)
        let french = PeopleLines.peopleLine(ids(9), order: [], locale: Vectors.fr, namedInFull: PeopleLines.namedOnCard)
        #expect(french.othersWords == "sept")
    }

    @Test("the Spine's persons order still leads when the Spine opened the card")
    func spineOrder() {
        let line = PeopleLines.peopleLine(["a", "b", "c"], order: ["c", "a"], locale: Vectors.en,
                                          namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ["c", "a", "b"])
    }
}
