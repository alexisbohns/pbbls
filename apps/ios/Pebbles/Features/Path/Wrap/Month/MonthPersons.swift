import SwiftUI

/// The month's five most present souls and a *more* to all of them (#1226,
/// D6); the invitation when nobody is named. Android: `PersonsSection`.
struct PersonsSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    let onMore: () -> Void

    @Environment(\.monthCardRoom) private var room

    private var locale: Locale { SpineStrings.appLocale }

    /// A person's row on a card: its share of the room, within the row minimum and this (D13).
    static let rowCap: CGFloat = 96
    static let glyphCap: CGFloat = 48
    static let ringCap: CGFloat = 60

    var body: some View {
        if Self.isShown(wrap: wrap, lookups: lookups) {
            let rows = Self.rows(wrap, lookups: lookups)
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(Self.head(wrap: wrap, lookups: lookups, dayView: dayView))
                if rows.isEmpty {
                    SheetLead(WrapCopy.nobody(locale: locale), bottom: 0)
                        .padding(.horizontal, MonthLayout.sectionPadX)
                        .introReveal(.lead)
                } else {
                    VStack(spacing: 0) {
                        let shown = Array(rows.prefix(WrapCopy.shown).enumerated())
                        let size = room.map { Self.rowSize(room: $0, rows: shown.count) }
                        ForEach(shown, id: \.element.soulId) { index, person in
                            MonthPersonRow(person: person, wrap: wrap, lookups: lookups, dayView: dayView,
                                           introRow: index, size: size)
                        }
                    }
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    // On a card, *more* stands at its foot (D13).
                    if room != nil { Spacer(minLength: 0) }
                    MoreLink(text: WrapCopy.morePersons(rows.count, locale: locale), action: onMore)
                        .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                        .padding(.horizontal, MonthLayout.sectionPadX)
                        .introReveal(.more)
                }
            }
            .introSequence(.section(.persons)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// A person's row on a card (D13): its share of the room left by *more*, the glyph and the ring growing with it,
    /// each within its bounds.
    static func rowSize(room: CGFloat, rows: Int) -> MonthPersonRowSize {
        let height = min(max((room - MonthLayout.moreReserve) / CGFloat(max(rows, 1)), SpineLayout.rowMin), rowCap)
        return MonthPersonRowSize(height: height,
                                  glyph: min(max(height * 0.5, SpineLayout.mark), glyphCap),
                                  ring: min(max(height * 0.7, MonthLayout.rowRing), ringCap))
    }

    /// The section's head, the deck's title for its card (#1241 D11): on the circle, the ring's reading.
    static func head(wrap: MonthWrap, lookups: WrapLookups, dayView: DayView) -> MonthHead {
        let locale = SpineStrings.appLocale
        let clockwise = dayView == .circle && !rows(wrap, lookups: lookups).isEmpty
        return MonthHead(label: WrapCopy.tile(.persons, locale: locale),
                         hint: clockwise ? WrapCopy.clockwiseHint(locale: locale) : nil)
    }

    /// Drawn whenever the server sends the persons: the invitation stands in when nobody is named.
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        wrap.persons != nil
    }

    /// The souls the device can name, in the server's order.
    private static func rows(_ wrap: MonthWrap, lookups: WrapLookups) -> [PersonStat] {
        (wrap.persons ?? []).filter { lookups.soulName($0.soulId) != nil }
    }

    /// The persons' intro (#1241): the head, then each of the five in turn, or the invitation.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        let rows = rows(wrap, lookups: lookups)
        return IntroScore.persons(rings: rows.prefix(WrapCopy.shown).map(\.days), hasLead: rows.isEmpty)
    }
}

/// A soul of the month: glyph, name, its pebbles, *new since* when the server
/// says so, and its days as a ring or a calendar. Android: `MonthPersonRow`.
struct MonthPersonRow: View {
    let person: PersonStat
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    /// Its place in the month page's persons, for the intro (#1241); the sheet leaves it nil.
    var introRow: Int?
    /// Its size on a card (#1241 D13); nil, the list's.
    var size: MonthPersonRowSize?

    var body: some View {
        let locale = SpineStrings.appLocale
        let name = lookups.soulName(person.soulId) ?? ""
        let fresh = person.isNewSincePrevious
            ? WrapCopy.newSince(wrap.month.previous, locale: locale) : nil
        MarkRow(name, accessibilityText: [name, "\(person.count)", fresh].compactMap { $0 }.joined(separator: ", "),
                subtitle: fresh, count: person.count,
                reveal: introRow.map { MarkRowReveal(leading: .glyph($0), text: .name($0)) },
                height: size?.height, markWidth: size.map { max($0.glyph, SpineLayout.markColumn) }) {
            SoulMark(strokes: lookups.soulGlyph(person.soulId), side: size?.glyph ?? SpineLayout.mark)
        } trailing: {
            DaySetMarks(days: person.days, mode: wrap.dayMode(dayView), ringSize: size?.ring ?? MonthLayout.rowRing,
                        introRow: introRow)
                .dayMarksInset(dayView)
        }
    }
}

/// A person's row on a card (#1241 D13): its height, its glyph's side and its ring's.
struct MonthPersonRowSize: Equatable {
    let height: CGFloat
    let glyph: CGFloat
    let ring: CGFloat
}
