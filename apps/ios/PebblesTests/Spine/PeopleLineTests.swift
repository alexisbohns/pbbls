import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/people-line.test.ts at a04aeec5, verbatim,
// plus the card's `namedInFull: PeopleLines.namedOnCard` (stack E's contract).
@Suite("PeopleLines (web people-line.test.ts)")
struct PeopleLineTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    private func ids(_ n: Int) -> [String] { (0..<n).map { "s\($0)" } }

    @Test("draws no line for a solitary pebble")
    func solitary() {
        #expect(PeopleLines.peopleLine([], order: [], locale: V.en)
            == PeopleLine(named: [], others: 0, othersWords: "", marks: [], overflow: 0))
    }

    @Test(arguments: [1, 2, 3])
    func namesAll(_ n: Int) {
        let line = PeopleLines.peopleLine(ids(n), order: ids(n), locale: V.en)
        #expect(line.named == ids(n))
        #expect(line.others == 0 && line.marks.isEmpty && line.overflow == 0)
    }

    @Test("names two and folds the rest from four")
    func four() {
        #expect(PeopleLines.peopleLine(ids(4), order: ids(4), locale: V.en)
            == PeopleLine(named: ["s0", "s1"], others: 2, othersWords: "two", marks: ["s2", "s3"], overflow: 0))
    }

    @Test("draws at most five marks at seven")
    func seven() {
        let line = PeopleLines.peopleLine(ids(7), order: ids(7), locale: V.en)
        #expect(line.named.count == 2)
        #expect(line.others == 5 && line.othersWords == "five")
        #expect(line.marks == ["s2", "s3", "s4", "s5", "s6"])
        #expect(line.overflow == 0)
    }

    @Test("reads 'with A, B & twelve others [5 marks] +7' at fourteen")
    func fourteen() {
        let line = PeopleLines.peopleLine(ids(14), order: ids(14), locale: V.en)
        #expect(line.named == ["s0", "s1"])
        #expect(line.others == 12 && line.othersWords == "twelve")
        #expect(line.marks.count == 5 && line.overflow == 7)
        #expect(PeopleLines.peopleLine(ids(14), order: ids(14), locale: V.fr).othersWords == "douze")
    }

    @Test("follows the persons-of-the-week order, not the pebble's")
    func order() {
        let line = PeopleLines.peopleLine(["elio", "mara", "nadia", "ines"], order: ["nadia", "ines", "mara", "elio"],
                                          locale: V.en)
        #expect(line.named == ["nadia", "ines"])
        #expect(line.marks == ["mara", "elio"])
    }

    @Test("keeps unranked souls after the ranked ones, in their own order")
    func unranked() {
        #expect(PeopleLines.peopleLine(["x", "mara", "y"], order: ["mara"], locale: V.en).named == ["mara", "x", "y"])
    }

    @Test("counts a duplicated soul once")
    func duplicated() {
        #expect(PeopleLines.peopleLine(["a", "a", "b"], order: ["a", "b"], locale: V.en).named == ["a", "b"])
    }

    @Test("the card names up to eight in full, then folds as the line does")
    func card() {
        #expect(PeopleLines.namedOnCard == 8)
        let eight = PeopleLines.peopleLine(ids(8), order: ids(8), locale: V.en, namedInFull: PeopleLines.namedOnCard)
        #expect(eight.named == ids(8) && eight.others == 0)
        let nine = PeopleLines.peopleLine(ids(9), order: ids(9), locale: V.en, namedInFull: PeopleLines.namedOnCard)
        #expect(nine.named == ["s0", "s1"] && nine.others == 7 && nine.overflow == 2)
    }
}
