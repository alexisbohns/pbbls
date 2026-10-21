import Testing
@testable import Pebbles

/// #1130 R7: "with [g] MARA, [g] ELIO & twelve others [g][g][g][g][g] +7" as
/// parts; the card's strip drops "with", the commas and the "&".
@Suite("People line parts")
struct PeopleLinePartsTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    private let names = ["a": "Mara", "b": "Elio", "c": "Nadia"]

    private func line(_ ids: [String]) -> PeopleLine {
        PeopleLines.peopleLine(ids, order: ids, locale: V.en)
    }

    @Test("one, two and three names: A · A & B · A, B & C")
    func names3() {
        #expect(PeopleLineParts.parts(line(["a"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara")])
        #expect(PeopleLineParts.parts(line(["a", "b"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara"), .aside("&"), .mark("b"), .name("Elio")])
        #expect(PeopleLineParts.parts(line(["a", "b", "c"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara"), .separator(","), .mark("b"), .name("Elio"),
                .aside("&"), .mark("c"), .name("Nadia")])
    }

    @Test("fourteen: two named, the others in words, five marks and +7")
    func fourteen() {
        let ids = ["a", "b"] + (0..<12).map { "s\($0)" }
        let parts = PeopleLineParts.parts(line(ids), names: names, with: "with", others: "& twelve others",
                                          style: .line)
        #expect(parts.prefix(6) == [.aside("with"), .mark("a"), .name("Mara"), .separator(","),
                                    .mark("b"), .name("Elio")])
        #expect(parts[6] == .aside("& twelve others"))
        #expect(parts.filter { if case .mark = $0 { return true } else { return false } }.count == 7)
        #expect(parts.last == .more("+7"))
    }

    @Test("a solitary pebble has no line at all")
    func solitary() {
        #expect(PeopleLineParts.parts(line([]), names: names, with: "with", others: "", style: .line).isEmpty)
    }

    @Test("the card's strip keeps no with, no commas and no ampersand")
    func card() {
        #expect(PeopleLineParts.parts(line(["a", "b", "c"]), names: names, with: "with", others: "", style: .card)
            == [.mark("a"), .name("Mara"), .mark("b"), .name("Elio"), .mark("c"), .name("Nadia")])
    }
}
