import Foundation

/// A week the Spine's links turn to (#1130 D9): the masthead's way back and the
/// mouth's way on, named by the ISO week of the page it reaches. The roll holds
/// only the weeks with moments plus the current one, so the calendar's
/// neighbour and the pager's differ whenever weeks are skipped (Android lesson,
/// Part 6), and a link must read as where it goes.
struct WeekNeighbour: Equatable {
    let number: Int
    let weekStart: Date
}

/// One page of the pager, as values (§Data flow), built once per load by `PathModel`.
struct SpinePage: Identifiable {
    let weekStart: Date
    let week: SpineWeek<MomentSummary>
    let words: WeekWords
    let persons: [PersonOfWeek]
    /// Every moment in reading order (`SpineWeekBuilder.readingOrder`).
    let contents: [MomentSummary]
    let previous: WeekNeighbour?
    let next: WeekNeighbour?
    let isCurrent: Bool
    /// Lowercase soul id → name: the reference cache, then the names the pebbles carry.
    let soulNames: [String: String]
    /// Lowercase soul id → glyph strokes, from the reference cache; a miss draws the dashed mark.
    let soulGlyphs: [String: [GlyphStroke]]

    var id: Date { weekStart }

    /// The persons-of-the-week order every people line on this page follows.
    var soulOrder: [String] { persons.map(\.soulId) }
}
