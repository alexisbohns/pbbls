import Foundation
import Observation

/// The souls chooser's state (souls spec S1–S3): ranking, grouping, folded
/// search, the Add-row rule and the ordered selection. Pure: hosts feed it
/// `ReferenceDataService.souls` and read the selection back. Views own no
/// state of their own beyond focus.
@Observable
@MainActor
final class SoulChooserModel {

    enum Mode: Equatable {
        /// Tiles and rows toggle; the tray shows pills and a button.
        /// `allowsSkip`: the record flow says "Skip" with nothing chosen, the
        /// edit sheet says "Done".
        case pick(allowsSkip: Bool)
        /// The People tab: tiles and rows open a person, no selection.
        case browse
    }

    struct Section: Identifiable, Equatable {
        let letter: String
        let souls: [SoulWithGlyph]
        var id: String { letter }
    }

    enum ButtonLabel: Equatable {
        case done(Int)
        case doneEmpty
        case skip
    }

    /// Two rows of four.
    static let usualCap = 8

    let mode: Mode
    private(set) var souls: [SoulWithGlyph]
    /// Ordered as chosen, so the pills keep their order across re-renders.
    private(set) var selection: [UUID]
    var query = ""
    var isSearching = false

    init(mode: Mode, souls: [SoulWithGlyph], selection: [UUID] = []) {
        self.mode = mode
        self.souls = souls
        self.selection = selection
    }

    // MARK: Ranking and grouping (S1)

    /// The most-tagged people: count descending, name ascending, at most eight,
    /// nobody with zero pebbles. The Life screen's strip takes the first four.
    var usual: [SoulWithGlyph] { Self.usual(from: souls) }

    static func usual(from souls: [SoulWithGlyph]) -> [SoulWithGlyph] {
        souls
            .filter { $0.pebblesCount > 0 }
            .sorted {
                if $0.pebblesCount != $1.pebblesCount { return $0.pebblesCount > $1.pebblesCount }
                return $0.name.localizedStandardCompare($1.name) == .orderedAscending
            }
            .prefix(usualCap)
            .map { $0 }
    }

    /// Everyone, grouped under the folded initial; "#" last for names that do
    /// not start with a letter. Names sort locale-aware inside a group.
    var sections: [Section] {
        let sorted = souls.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        var order: [String] = []
        var grouped: [String: [SoulWithGlyph]] = [:]
        for soul in sorted {
            let letter = Self.letter(for: soul.name)
            if grouped[letter] == nil { order.append(letter) }
            grouped[letter, default: []].append(soul)
        }
        let named = order
            .filter { $0 != "#" }
            .sorted { $0.localizedStandardCompare($1) == .orderedAscending }
        let letters = named + (order.contains("#") ? ["#"] : [])
        return letters.map { Section(letter: $0, souls: grouped[$0] ?? []) }
    }

    static func folded(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).lowercased()
    }

    static func letter(for name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = folded(trimmed).first, first.isLetter else { return "#" }
        return String(first).uppercased()
    }

    // MARK: Search (S3)

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Matches anywhere in the folded name. Empty without a query.
    var results: [SoulWithGlyph] {
        let needle = Self.folded(trimmedQuery)
        guard !needle.isEmpty else { return [] }
        return souls
            .filter { Self.folded($0.name).contains(needle) }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// The Add row and the lit plus: a query nobody's folded name equals.
    var canAdd: Bool {
        let needle = Self.folded(trimmedQuery)
        guard !needle.isEmpty else { return false }
        return !souls.contains { Self.folded($0.name) == needle }
    }

    // MARK: Selection (S2)

    func isSelected(_ id: UUID) -> Bool { selection.contains(id) }

    func toggle(_ id: UUID) {
        guard case .pick = mode else { return }
        if let index = selection.firstIndex(of: id) {
            selection.remove(at: index)
        } else {
            selection.append(id)
        }
    }

    func remove(_ id: UUID) {
        guard case .pick = mode else { return }
        selection.removeAll { $0 == id }
    }

    var buttonLabel: ButtonLabel? {
        switch mode {
        case .browse:
            return nil
        case .pick(let allowsSkip):
            if selection.isEmpty { return allowsSkip ? .skip : .doneEmpty }
            return .done(selection.count)
        }
    }

    // MARK: Souls lifecycle

    /// A fresh fetch (reference data refreshed behind the sheet). A chosen soul
    /// that is gone is dropped, which costs a pill, not a `soul_not_owned`.
    func replaceSouls(_ souls: [SoulWithGlyph]) {
        self.souls = souls
        let known = Set(souls.map(\.id))
        selection = selection.filter { known.contains($0) }
    }

    /// The inline create (S4): the new soul joins the list, arrives chosen in a
    /// picker, and the field closes.
    func didCreate(_ soul: SoulWithGlyph) {
        if !souls.contains(where: { $0.id == soul.id }) { souls.append(soul) }
        if case .pick = mode, !selection.contains(soul.id) { selection.append(soul.id) }
        query = ""
        isSearching = false
    }
}
