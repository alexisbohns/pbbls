import SwiftUI

/// The month's cover (#1226, D7): the garland with the month inside it (its
/// place in the year, its name, the year, the souls' glyphs), then the summary,
/// the persons in order of appearance and the colophon of inks and papers. Only
/// the server's numbers and orders; the words are `WrapCopy`'s. The ring's
/// inner text keeps the default size, its geometry being fixed (D11): the
/// summary, the names and the colophon below it scale. Android: `MonthCover`.
struct MonthCoverView: View {
    let wrap: MonthWrap
    let lookups: WrapLookups

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if let cover = wrap.cover {
            let souls = cover.soulsInOrder.filter { lookups.soulName($0) != nil }
            VStack(spacing: 10) {
                Garland(wrap: wrap, cover: cover, lookups: lookups) {
                    VStack(spacing: 0) {
                        Text(verbatim: WrapCopy.monthKicker(wrap.month, locale: locale))
                            .spineType(.sectionLabel)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        Text(verbatim: WrapCopy.monthName(wrap.month, locale: locale))
                            .spineType(.monthName)
                            .foregroundStyle(Color.pebbles.onSurface)
                        Text(verbatim: String(wrap.month.year))
                            .spineType(.contentsTime)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        if !souls.isEmpty {
                            HStack(spacing: 2) {
                                ForEach(souls.prefix(8), id: \.self) { id in
                                    SoulMark(strokes: lookups.soulGlyph(id), side: 15)
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
                Text(verbatim: WrapCopy.line(pebbles: wrap.pebbles, people: wrap.people, live: false, hasMoments: true,
                                             locale: locale))
                    .spineType(.summary)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
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
                }
                colophon(cover)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, MonthLayout.sectionPadX)
            .padding(.top, 20)
        }
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
