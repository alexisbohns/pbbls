import SwiftUI

/// The month's cover (#1226, D7): the garland with the month inside it (its
/// place in the year, its name, the year, the souls' glyphs), then the summary,
/// the persons in order of appearance and the colophon of inks and papers. Only
/// the server's numbers and orders; the words are `WrapCopy`'s. The ring's
/// inner text keeps the default size, its geometry being fixed (D11): the
/// summary, the names and the colophon below it scale. In the month's intro
/// (#1241) it is the first sequence: the name, the garland, then the words.
/// Android: `MonthCover`.
struct MonthCoverView: View {
    let wrap: MonthWrap
    let lookups: WrapLookups

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if Self.isShown(wrap: wrap, lookups: lookups), let cover = wrap.cover {
            let souls = Self.souls(cover, lookups: lookups)
            VStack(spacing: 10) {
                IntroReader { reveal in
                    Garland(wrap: wrap, cover: cover, lookups: lookups, reveal: reveal) {
                        VStack(spacing: 0) {
                            Text(verbatim: WrapCopy.monthKicker(wrap.month, locale: locale))
                                .spineType(.sectionLabel)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                .introReveal(.kicker)
                            Text(verbatim: WrapCopy.monthName(wrap.month, locale: locale))
                                .spineType(.monthName)
                                .foregroundStyle(Color.pebbles.onSurface)
                                .introReveal(.monthName)
                            Text(verbatim: String(wrap.month.year))
                                .spineType(.contentsTime)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                .introReveal(.year)
                            if !souls.isEmpty {
                                HStack(spacing: 2) {
                                    ForEach(Array(souls.prefix(Self.soulMarks).enumerated()),
                                            id: \.element) { index, id in
                                        SoulMark(strokes: lookups.soulGlyph(id), side: 15)
                                            .introReveal(.soul(index), .pop)
                                    }
                                }
                                .padding(.top, 6)
                                .accessibilityHidden(true)
                            }
                        }
                        .dynamicTypeSize(...DynamicTypeSize.large)
                        .multilineTextAlignment(.center)
                        // One stop for VoiceOver: "part the tenth, October, 2026", the page's header.
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isHeader)
                    }
                }
                Text(verbatim: WrapCopy.line(pebbles: wrap.pebbles, people: wrap.people, live: false, hasMoments: true,
                                             locale: locale))
                    .spineType(.summary)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .introReveal(.summary)
                if !souls.isEmpty {
                    VStack(spacing: 2) {
                        Text(verbatim: WrapCopy.personsInOrderLabel(locale: locale))
                            .spineType(.sectionHint)
                            .foregroundStyle(Color.pebbles.outline)
                        Text(verbatim: souls.compactMap(lookups.soulName).joined(separator: "\u{00A0}· "))
                            .spineType(.soulName)
                            .foregroundStyle(Color.pebbles.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .multilineTextAlignment(.center)
                    .accessibilityElement(children: .combine)
                    .introReveal(.names)
                }
                colophon(cover)
                    .introReveal(.colophon)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, MonthLayout.sectionPadX)
            .padding(.top, 20)
            .introSequence(.cover) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// Drawn when the server sends a cover (#1241 D11: the deck's first card).
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        wrap.cover != nil
    }

    /// At most this many souls' glyphs inside the ring.
    private static let soulMarks = 8

    /// The souls the device can name, in the cover's order.
    private static func souls(_ cover: MonthCover, lookups: WrapLookups) -> [String] {
        cover.soulsInOrder.filter { lookups.soulName($0) != nil }
    }

    /// The cover's intro (#1241): its days, the glyphs it draws and the lines it shows.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        guard let cover = wrap.cover else { return [] }
        let souls = souls(cover, lookups: lookups)
        let papers = cover.papers.compactMap { lookups.domain($0) }
        let lines: [IntroKey] = [.summary] + (souls.isEmpty ? [] : [.names])
            + (cover.inks.isEmpty && papers.isEmpty ? [] : [.colophon])
        return IntroScore.cover(days: cover.days, daysInMonth: wrap.daysInMonth, numbered: wrap.numberedDays,
                                souls: min(souls.count, soulMarks), lines: lines)
    }

    /// "Printed in *joy*, *peace* & *pride*, … on family, identity & friends.", each ink in its family's colour.
    @ViewBuilder
    private func colophon(_ cover: MonthCover) -> some View {
        let inks = cover.inks.map { lookups.familyName($0).lowercased(with: locale) }
        let papers = cover.papers.compactMap { lookups.domain($0)?.localizedName.lowercased(with: locale) }
        if !inks.isEmpty || !papers.isEmpty {
            let sentence = WrapCopy.colophon(inks: inks, papers: papers, locale: locale)
            Text(inked(sentence, names: zip(cover.inks, inks).map { slug, name in
                (name, WrapInk.spot(lookups.family(slug)?.palette, scheme: scheme))
            }))
            .spineType(.still)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .multilineTextAlignment(.center)
        }
    }

    /// Colours each name where it first appears after the previous one.
    private func inked(_ sentence: String, names: [(String, Color)]) -> AttributedString {
        var text = AttributedString(sentence)
        var from = text.startIndex
        for (name, color) in names {
            guard let range = text[from...].range(of: name, options: .caseInsensitive) else { continue }
            text[range].foregroundColor = color
            from = range.upperBound
        }
        return text
    }
}
