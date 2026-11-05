import Foundation
import Testing
@testable import Pebbles

@Suite("SoulChooserModel")
@MainActor
struct SoulChooserModelTests {

    private static let glyph = Glyph(
        id: SystemGlyph.default, name: nil,
        strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
        viewBox: "0 0 200 200", userId: nil
    )

    private func soul(_ name: String, _ count: Int) -> SoulWithGlyph {
        SoulWithGlyph(id: UUID(), name: name, glyphId: SystemGlyph.default, glyph: Self.glyph, pebblesCount: count)
    }

    private func names(_ souls: [SoulWithGlyph]) -> [String] { souls.map(\.name) }

    @Test("usual = count desc then name asc, capped at eight, zero counts excluded")
    func usual() {
        let souls = [soul("Zoé", 3), soul("Adèle", 3), soul("Ben", 0), soul("Camille", 29),
                     soul("Alex", 24), soul("Léa", 5), soul("Papa", 5), soul("Inès", 4),
                     soul("Sam", 4), soul("Nour", 2), soul("Mamie Jo", 2)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        #expect(names(model.usual) == ["Camille", "Alex", "Léa", "Papa", "Inès", "Sam", "Adèle", "Zoé"])
    }

    @Test("no usual section when nobody has a pebble")
    func usualEmpty() {
        let model = SoulChooserModel(mode: .browse, souls: [soul("Ben", 0), soul("Ana", 0)])
        #expect(model.usual.isEmpty)
    }

    @Test("A–Z sections fold diacritics, keep duplicates, and put non-letters under #")
    func sections() {
        let souls = [soul("Élise", 1), soul("alex", 2), soul("Alex", 3), soul("1er voisin", 0), soul("Ben", 0)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        #expect(model.sections.map(\.letter) == ["A", "B", "E", "#"])
        #expect(names(model.sections[0].souls) == ["alex", "Alex"])
        #expect(names(model.sections[2].souls) == ["Élise"])
        #expect(names(model.sections[3].souls) == ["1er voisin"])
    }

    @Test("search is case- and diacritic-insensitive and matches inside the name")
    func search() {
        let souls = [soul("Mathilde", 1), soul("Théo", 2), soul("Mamie Jo", 0)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        model.query = "theo"
        #expect(names(model.results) == ["Théo"])
        model.query = "  MIE  "
        #expect(names(model.results) == ["Mamie Jo"])
        model.query = ""
        #expect(model.results.isEmpty)
    }

    @Test("the Add row appears only without an exact folded match")
    func canAdd() {
        let model = SoulChooserModel(mode: .browse, souls: [soul("Théo", 2)])
        #expect(!model.canAdd)
        model.query = "theo"
        #expect(!model.canAdd)
        model.query = "Théo "
        #expect(!model.canAdd)
        model.query = "Mathilde"
        #expect(model.canAdd)
        #expect(model.trimmedQuery == "Mathilde")
    }

    @Test("selection toggles in order; the pick button counts, skip when empty and allowed")
    func selection() {
        let a = soul("Ana", 1), b = soul("Ben", 1)
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [a, b])
        #expect(model.buttonLabel == .skip)
        model.toggle(b.id)
        model.toggle(a.id)
        #expect(model.selection == [b.id, a.id])
        #expect(model.isSelected(a.id))
        #expect(model.buttonLabel == .done(2))
        model.remove(b.id)
        #expect(model.selection == [a.id])
        model.toggle(a.id)
        #expect(model.selection.isEmpty)
        #expect(model.buttonLabel == .skip)
    }

    @Test("the edit sheet says Done with nothing chosen; browse has no button")
    func buttonLabels() {
        #expect(SoulChooserModel(mode: .pick(allowsSkip: false), souls: []).buttonLabel == .doneEmpty)
        #expect(SoulChooserModel(mode: .browse, souls: []).buttonLabel == nil)
    }

    @Test("a created soul lands in the list, chosen in pick mode, and the search closes")
    func didCreate() {
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [soul("Ben", 0)])
        model.isSearching = true
        model.query = "Mathilde"
        let created = soul("Mathilde", 0)
        model.didCreate(created)
        #expect(model.souls.map(\.name).contains("Mathilde"))
        #expect(model.selection == [created.id])
        #expect(model.query.isEmpty)
        #expect(!model.isSearching)

        let browse = SoulChooserModel(mode: .browse, souls: [])
        browse.didCreate(created)
        #expect(browse.selection.isEmpty)
    }

    @Test("replacing the souls keeps a selection that still exists and drops one that does not")
    func replaceSouls() {
        let a = soul("Ana", 1), b = soul("Ben", 1)
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [a, b], selection: [a.id, b.id])
        model.replaceSouls([a])
        #expect(model.selection == [a.id])
    }

    @Test("didCreate is idempotent, also after replaceSouls already carries the soul")
    func didCreateIdempotent() {
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [])
        let created = soul("Mathilde", 0)
        model.didCreate(created)
        model.didCreate(created)
        #expect(model.souls.count == 1)
        #expect(model.selection == [created.id])

        let other = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [])
        other.replaceSouls([created])
        other.didCreate(created)
        #expect(other.souls.count == 1)
        #expect(other.selection == [created.id])
    }

    @Test("toggle and remove are no-ops in browse")
    func browseNoSelection() {
        let a = soul("Ana", 1)
        let model = SoulChooserModel(mode: .browse, souls: [a], selection: [a.id])
        model.toggle(UUID())
        model.remove(a.id)
        #expect(model.selection == [a.id])
    }
}
