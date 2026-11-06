import Foundation
import Testing
@testable import Pebbles

/// The rock cut both ways (#1510 D7). The row seeds are Android's `StoneGrid.rowSeeds`, so the
/// pieces match across surfaces.
@Suite("Stone grid")
struct StoneGridTests {
    @Test("a row's seed is the stone's seed and its row, as on Android")
    func rowSeeds() {
        #expect(StoneGridLayout.rowSeeds(seed: "onboarding-glyphs", rows: 3)
                == ["onboarding-glyphs#0", "onboarding-glyphs#1", "onboarding-glyphs#2"])
        #expect(StoneGridLayout.rowSeeds(seed: "onboarding-glyphs-folded", rows: 1) == ["onboarding-glyphs-folded#0"])
    }

    @Test("cells are numbered row-major")
    func cellIndex() {
        #expect(StoneGridLayout.cellIndex(row: 0, column: 0, columns: 3) == 0)
        #expect(StoneGridLayout.cellIndex(row: 1, column: 2, columns: 3) == 5)
        #expect(StoneGridLayout.cellIndex(row: 2, column: 1, columns: 3) == 7)
        #expect(StoneGridLayout.cellIndex(row: 0, column: 3, columns: 4) == 3)
    }
}
