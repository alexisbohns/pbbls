import SwiftUI

/// A moment without a photo, set as type (#1130 R6): the name in its intensity
/// size and valence cut (at most 22 ch wide, no line limit), the meta 8 pt below
/// (time · EMOTION in the spot ink), the people line 6 pt below. A solitary
/// pebble has no people line, and is never marked "alone". `SpinePebble` wraps
/// it in the slot's button, which spans the column (Android lesson).
struct SpineEntry: View {
    let moment: MomentSummary
    let palette: StonePalette
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]

    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    /// Copy reads the app's language; the time adds the device's hour cycle and region.
    private var locale: Locale { SpineStrings.appLocale }
    private var dateLocale: Locale { SpineStrings.dateLocale }
    private var calendar: Calendar { SpineCalendar.current }

    static func type(for moment: MomentSummary) -> EntryType {
        EntryTypes.entryType(intensity: moment.pebble?.intensity ?? 2,
                             valence: moment.pebble?.valence.positiveness ?? 0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            name
            meta.padding(.top, 8)
            people.padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var name: some View {
        let type = Self.type(for: moment)
        let size = SpineType.resolve(.entryName(type.name), dynamicTypeSize: dynamicTypeSize,
                                     legibilityWeight: legibilityWeight).size
        if let name = moment.name, !name.isEmpty {
            Text(verbatim: name)
                .spineType(.entryName(type.name))
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(maxWidth: 22 * 0.55 * size, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            Text(verbatim: SpineCopy.untitled(locale: locale))
                .spineType(.entryName(.untitled(size: type.name.size)))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    private var meta: some View {
        HStack(spacing: 6) {
            Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: dateLocale))
                .spineType(.entryTime)
                .foregroundStyle(Color.pebbles.onSurface)
            if let emotion = moment.pebble?.emotion {
                Text(verbatim: "·")
                    .spineType(.entryTime)
                    .foregroundStyle(Color.pebbles.outline)
                Text(verbatim: emotion.localizedName)
                    .spineType(.entryEmotion)
                    .foregroundStyle(SpineColors.spot(palette, scheme: scheme))
            }
        }
    }

    @ViewBuilder
    private var people: some View {
        let line = PeopleLines.peopleLine(moment.soulIds, order: soulOrder, locale: locale)
        let others = SpineCopy.others(count: line.others, words: line.othersWords, locale: locale)
        let parts = PeopleLineParts.parts(line, names: soulNames, with: SpineCopy.withWord(locale: locale),
                                          others: others, style: .line)
        if !parts.isEmpty {
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 14, base: .people, name: .soulName,
                           aside: .peopleAside, more: .entryTime)
        }
    }
}
