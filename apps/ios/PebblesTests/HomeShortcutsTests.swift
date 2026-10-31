import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("Home shortcuts")
struct HomeShortcutsTests {
    @Test func bentoLeadsWithTheLastUsedAndKeepsTheOrder() {
        #expect(HomeShortcut.bento(lead: .camera) == [.camera, .quick, .pebble])
        #expect(HomeShortcut.bento(lead: .quick) == [.quick, .camera, .pebble])
        #expect(HomeShortcut.bento(lead: .pebble) == [.pebble, .camera, .quick])
    }

    /// c3's row (#1382 D11): icon top, label bottom, at least 100 pt tall.
    @Test func theRowIsAtLeastAHundredPointsTall() {
        #expect(HomeShortcuts.rowMinHeight == 100)
    }

    /// The stored values: renaming one would reset every device's choice.
    @Test func storedValuesAreStable() {
        #expect(HomeShortcutsLayout.allCases.map(\.rawValue) == ["row", "bento"])
        #expect(HomeShortcut.allCases.map(\.rawValue) == ["camera", "quick", "pebble"])
    }

    /// The exploration harness's `QA.rng`, bit for bit (values from node).
    @Test func randomMatchesTheHarness() {
        var rock = StoneRandom("camera|fixed|rock|q11")
        let rockDraws = [0.9302541588, 0.5075708053, 0.6537410403]
        for expected in rockDraws { #expect(abs(Double(rock.next()) - expected) < 1e-9) }
        var split = StoneRandom("quick|fixed|q12split")
        let splitDraws = [0.0461277429, 0.2574864214, 0.5876142546]
        for expected in splitDraws { #expect(abs(Double(split.next()) - expected) < 1e-9) }
    }

    @Test(arguments: [CGSize(width: 359, height: 84), CGSize(width: 359, height: 98), CGSize(width: 300, height: 120)])
    func rowPiecesStayInTheirCells(size: CGSize) {
        let pieces = ShortcutStones.splitRow(width: size.width, height: size.height)
        let cells = ShortcutStones.rowCells(size: size)
        #expect(pieces.count == 3)
        for (piece, cell) in zip(pieces, cells) {
            #expect(!piece.isEmpty)
            // The cuts lean, so a piece may reach a few points past its column, never into the next one.
            let xs = piece.map(\.x), ys = piece.map(\.y)
            #expect(xs.min()! >= cell.minX - ShortcutStones.gutter - 0.5)
            #expect(xs.max()! <= cell.maxX + ShortcutStones.gutter + 0.5)
            #expect(ys.min()! >= -0.5 && ys.max()! <= size.height + 0.5)
        }
        // The gutters stay open: no piece reaches its neighbour.
        for i in 0..<2 {
            let rightOfLeft = pieces[i].filter { abs($0.y - size.height / 2) < 1 }.map(\.x).max()!
            let leftOfRight = pieces[i + 1].filter { abs($0.y - size.height / 2) < 1 }.map(\.x).min()!
            #expect(leftOfRight - rightOfLeft > ShortcutStones.gutter - 1)
        }
    }

    @Test(arguments: HomeShortcut.allCases)
    func bentoPiecesStayInTheirCells(lead: HomeShortcut) {
        let size = CGSize(width: 359, height: 162)
        let leadWidth = ShortcutsBentoLayout.leadWidth(for: size.width)
        let pieces = ShortcutStones.splitBento(size: size, leadWidth: leadWidth, lead: lead)
        let cells = ShortcutStones.bentoCells(size: size, leadWidth: leadWidth)
        #expect(pieces.count == 3)
        for (piece, cell) in zip(pieces, cells) {
            #expect(piece.count > 24)
            #expect(piece.allSatisfy { cell.insetBy(dx: -0.5, dy: -0.5).contains($0) })
        }
    }

    @Test func shapesAreFixed() {
        let a = ShortcutStones.splitRow(width: 359, height: 98)
        let b = ShortcutStones.splitRow(width: 359, height: 98)
        #expect(a == b)
        let c = ShortcutStones.splitBento(size: CGSize(width: 359, height: 162), leadWidth: 162, lead: .quick)
        let d = ShortcutStones.splitBento(size: CGSize(width: 359, height: 162), leadWidth: 162, lead: .quick)
        #expect(c == d)
    }
}
