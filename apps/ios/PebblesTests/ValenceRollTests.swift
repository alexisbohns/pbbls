import Testing
@testable import Pebbles

/// The roll's behaviour lives in index arithmetic, so it can be asserted
/// without a gesture: what a step lands on, where it stops, and what the
/// affordances promise.
@Suite("Valence roll")
struct ValenceRollTests {

    @Test("the size ladder runs large at the top to small at the bottom")
    func ladderOrder() {
        #expect(ValenceSizeGroup.ladder == [.large, .medium, .small])
        // Not allCases, which runs the other way — the roll stacks a big event
        // above a small one.
        #expect(ValenceSizeGroup.ladder != ValenceSizeGroup.allCases)
    }

    @Test("indices round-trip through the grid")
    func indicesRoundTrip() {
        for valence in Valence.allCases {
            let rebuilt = Valence.at(polarityIndex: valence.polarityIndex, sizeIndex: valence.sizeIndex)
            #expect(rebuilt == valence)
        }
    }

    @Test("a step moves one cell along one axis")
    func stepMovesOneCell() {
        let start = Valence.neutralMedium
        #expect(Valence.at(polarityIndex: start.polarityIndex - 1, sizeIndex: start.sizeIndex)
            == .lowlightMedium)
        #expect(Valence.at(polarityIndex: start.polarityIndex + 1, sizeIndex: start.sizeIndex)
            == .highlightMedium)
        // Up the ladder is bigger, down is smaller.
        #expect(Valence.at(polarityIndex: start.polarityIndex, sizeIndex: start.sizeIndex - 1)
            == .neutralLarge)
        #expect(Valence.at(polarityIndex: start.polarityIndex, sizeIndex: start.sizeIndex + 1)
            == .neutralSmall)
    }

    /// A hard swipe must not loop past the end and back to where it started.
    @Test("the ends clamp instead of wrapping")
    func endsClamp() {
        #expect(Valence.at(polarityIndex: -5, sizeIndex: 1) == .lowlightMedium)
        #expect(Valence.at(polarityIndex: 9, sizeIndex: 1) == .highlightMedium)
        #expect(Valence.at(polarityIndex: 1, sizeIndex: -5) == .neutralLarge)
        #expect(Valence.at(polarityIndex: 1, sizeIndex: 9) == .neutralSmall)
    }

    /// The pyramid draws all three marks and lights the current one, so the
    /// only thing to assert is that the ladder covers every size exactly once.
    @Test("the pyramid has a mark for every size")
    func pyramidCoversEverySize() {
        #expect(Set(ValenceSizeGroup.ladder) == Set(ValenceSizeGroup.allCases))
        #expect(ValenceSizeGroup.ladder.count == ValenceSizeGroup.allCases.count)

        for valence in Valence.allCases {
            let lit = ValenceSizeGroup.ladder.filter { $0 == valence.sizeGroup }
            #expect(lit.count == 1, "\(valence) lights \(lit.count) marks")
        }
    }

    @Test("neighbour polarities are nil only at the ends")
    func neighbourPolarities() {
        #expect(Valence.lowlightMedium.polarityBefore == nil)
        #expect(Valence.lowlightMedium.polarityAfter == .neutral)
        #expect(Valence.neutralMedium.polarityBefore == .lowlight)
        #expect(Valence.neutralMedium.polarityAfter == .highlight)
        #expect(Valence.highlightMedium.polarityBefore == .neutral)
        #expect(Valence.highlightMedium.polarityAfter == nil)
    }
}

@Suite("RecordFlowModel — valence seeding")
@MainActor
struct RecordFlowValenceSeedTests {

    @Test("seeding parks on neutral-medium and answers the step")
    func seedParks() {
        let model = RecordFlowModel()
        model.go(to: .valence)
        #expect(model.draft.valence == nil)
        #expect(model.isAnswered == false)

        model.seedValenceIfNeeded()

        #expect(model.draft.valence == .neutralMedium)
        #expect(model.isAnswered)
    }

    @Test("seeding never overwrites an answer the user already gave")
    func seedRespectsExisting() {
        let model = RecordFlowModel()
        model.go(to: .valence)
        model.select(valence: .highlightLarge)

        model.seedValenceIfNeeded()

        #expect(model.draft.valence == .highlightLarge)
    }
}
