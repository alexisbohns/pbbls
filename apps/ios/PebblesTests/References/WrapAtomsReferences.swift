import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W2: the wraps' shared atoms. The seal in its four sizes and three
    /// polarities (plus the blank), a day set as a row, a calendar and a circle,
    /// and the section header, more link, tiles, bar, dots and rows, Paper and
    /// Night (+ `-ax2`). Android: the `core/designsystem` and `core/ui` previews.
    @Suite("Wrap atoms references")
    @MainActor
    struct WrapAtomsReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func seals(scheme: ReferenceScheme) {
            let glyph = F.moments.first { $0.name == "Flag march with Nadia" }?.pebble?.glyph
            // Twice their size, each in a 72 pt cell (scaleEffect does not grow the layout).
            let view = VStack(alignment: .leading, spacing: 8) {
                ForEach([SealSize.contents, .milestone, .tiny, .pip], id: \.self) { size in
                    HStack(spacing: 8) {
                        ForEach(Valence.allCases, id: \.self) { valence in
                            StoneSeal(valence: valence, glyph: glyph, palette: .brand, isBlank: false, size: size)
                                .scaleEffect(2)
                                .frame(width: 72, height: 72)
                        }
                        StoneSeal(valence: .neutralMedium, glyph: nil, palette: .blank, isBlank: true, size: size)
                            .scaleEffect(2)
                            .frame(width: 72, height: 72)
                    }
                }
            }
            .padding(24)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "seals", config: ReferenceConfig(scheme: scheme, width: nil))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func days(scheme: ReferenceScheme) {
            let week = DaySet(length: 7, days: [0, 1, 3, 5])
            let october = DaySet(length: 31, days: [0, 4, 5, 6, 9, 13, 18, 20, 25, 28, 30])
            let view = VStack(alignment: .leading, spacing: 24) {
                DaySetMarks(days: week, mode: .row)
                DaySetMarks(days: october, mode: .calendar(leading: 3))
                HStack(spacing: 24) {
                    DaySetMarks(days: october, mode: .circle)
                    DaySetMarks(days: october, mode: .circle, ringSize: 40)
                    DaySetMarks(days: october, mode: .circle, ink: Color.pebbles.primary, ring: Color.pebbles.primary,
                                ringSize: 40)
                }
                HStack(spacing: 12) {
                    ForEach([DotStyle.filled, .ring, .socket, .quiet], id: \.self) { style in
                        DotMark(style: style, color: Color.pebbles.onSurface, diameter: 12)
                    }
                }
            }
            .padding(24)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "days", config: ReferenceConfig(scheme: scheme, width: nil))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pieces(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.pieces, named: "pieces", config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func piecesAx2() {
            ReferenceImage.assertMatches(Self.pieces, named: "pieces",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        private static func noop() {}

        private static var pieces: some View {
            let mara = F.soulGlyphs.values.first
            return VStack(alignment: .leading, spacing: 20) {
                SpineSectionHeader("Emotions", hint: "weighed by intensity") {
                    Text(verbatim: "M T W T F S S")
                        .spineType(.dayLetter)
                        .foregroundStyle(Color.pebbles.outline)
                }
                HStack(alignment: .top, spacing: 12) {
                    TileCard {} content: {
                        TileHeader(label: "Persons")
                        Text(verbatim: "Mara, Elio and three others")
                            .spineType(.personsFold)
                            .foregroundStyle(Color.pebbles.onSurface)
                            .padding(.top, 8)
                    }
                    TileCard(dashed: true) {} content: {
                        TileHeader(label: "Contents")
                        ProportionBar(segments: [
                            BarSegment(weight: 7, color: Color.pebbles.primary),
                            BarSegment(weight: 4, color: Color.pebbles.secondary),
                            BarSegment(weight: 0, color: Color.pebbles.error),
                            BarSegment(weight: 2, color: Color.pebbles.outline)
                        ])
                        .padding(.top, 12)
                    }
                }
                VStack(spacing: 0) {
                    MarkRow("Mara", accessibilityText: "Mara", count: 4) {
                        SoulMark(strokes: mara, side: SpineLayout.mark)
                    } trailing: {
                        DaySetMarks(days: DaySet(length: 7, days: [0, 1, 3, 5]), mode: .row)
                    }
                    MarkRow("Joy", accessibilityText: "Joy", titleType: .personsFold, subtitle: "seven pebbles",
                            action: Self.noop) {
                        DotMark(style: .filled, color: Color.pebbles.primary, diameter: 10)
                    }
                }
                MoreLink(text: "All twelve persons", action: {})
            }
            .padding(20)
            .background(Color.pebbles.surface)
        }
    }
}
