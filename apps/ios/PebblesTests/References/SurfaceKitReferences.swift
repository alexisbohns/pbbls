import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The Home and You surface kit (#1382 D6, D7, D15): stones, the card, the
    /// box, the section head and the link row, each on `surface`.
    @Suite("Surface kit references") @MainActor
    struct SurfaceKitReferences {
        private static let kitWidth: CGFloat = 358

        private static func page(_ content: some View) -> some View {
            content
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        private static func split(_ count: Int, seed: String, height: CGFloat, width: CGFloat = kitWidth,
                                  options: StoneOptions = StoneOptions(), fill: Color) -> some View {
            SplitStone(count: count, seed: seed, options: options, fills: [fill], minHeight: height) { _ in
                Color.clear.frame(height: height)
            }
            .frame(width: width)
        }

        /// The split stones at 3 (quick actions, counters) and 5 (the first-five
        /// stepper), the chips, the settings and community stones, the pentagon.
        static func stones() -> some View {
            page(VStack(alignment: .leading, spacing: 20) {
                split(3, seed: "camera|fixed|rock|q11", height: 100, fill: Color.pebbles.primaryContainer)
                split(3, seed: "days|pebbles|karma", height: 88, fill: Color.pebbles.surfaceContainer)
                SplitStone(count: 5, seed: "first-five",
                           options: StoneOptions(corner: 3, lean: 0.9, gutter: 5, fillet: 2),
                           fills: [Color.pebbles.primary, Color.pebbles.primary,
                                   Color.pebbles.surfaceContainerHighest, Color.pebbles.surfaceContainerHighest,
                                   Color.pebbles.surfaceContainerHighest],
                           minHeight: 6) { _ in Color.clear.frame(height: 6) }
                    .frame(width: 324)
                HStack(spacing: 14) {
                    ForEach(["Stones", "Emotions", "Souls"], id: \.self) { title in
                        StoneShape(seed: "chip-" + title, options: StoneOptions(corner: 16))
                            .fill(Color.pebbles.surfaceContainer)
                            .frame(width: PebblesLinkRow.chipSize.width, height: PebblesLinkRow.chipSize.height)
                    }
                    StoneShape(seed: "settings", options: StoneOptions(corner: 16))
                        .fill(Color.pebbles.surfaceContainerHigh)
                        .frame(width: 46, height: 44)
                        .overlay {
                            PbblsIconImage(.slider, .toolbar, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                        }
                }
                StoneShape(seed: "whatsapp", options: StoneOptions(corner: 30))
                    .fill(Color.pebbles.tertiaryContainer)
                    .frame(width: kitWidth, height: 76)
                SquirclePentagon()
                    .fill(Color.pebbles.primaryContainer)
                    .frame(width: 128, height: 122)
                    .overlay {
                        PbblsIconImage(.pebbleDottedGlyph, size: 58,
                                       style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                            .offset(y: 4)
                    }
                    .frame(maxWidth: .infinity)
            })
        }

        @Test(arguments: ReferenceScheme.allCases)
        func stones(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(Self.stones(), named: "stones", config: ReferenceConfig(scheme: scheme))
        }

        /// A card holding text, with room for its shadow, and the box.
        static func cardAndBox() -> some View {
            page(VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: "October").spineType(.wrapMonth).foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: "2026").spineType(.wrapYear).foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                .padding(EdgeInsets(top: 18, leading: 18, bottom: 18, trailing: 16))
                .frame(width: 152, alignment: .leading)
                .pebblesCard()
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: "Record your first moment").spineType(.rowTitle)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: "A time and a name. Nothing more.").spineType(.rowLine)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 14)
                .pebblesBox()
            })
        }

        @Test(arguments: ReferenceScheme.allCases)
        func cardAndBox(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(Self.cardAndBox(), named: "cardAndBox", config: ReferenceConfig(scheme: scheme))
        }

        /// The head plain, with a ratio and with a counted word, in the pass's language.
        static func sectionHeads() -> some View {
            let french = ReferenceLocale.current == .fr
            return page(VStack(alignment: .leading, spacing: 24) {
                PebblesSectionHead(String(localized: "home.about.title"))
                PebblesSectionHead(String(localized: "home.firstFive.title"),
                                   figure: .ratio(done: 2, total: 5, word: french ? "sur" : "of"))
                PebblesSectionHead(String(localized: "Achievements"),
                                   figure: .counted(12, word: french ? "décrochés" : "earned"))
            })
        }

        @Test(arguments: ReferenceScheme.allCases)
        func sectionHeads(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.sectionHeads(), named: "sectionHeads",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        /// A link row to go, and one done.
        static func linkRows() -> some View {
            page(VStack(alignment: .leading, spacing: 0) {
                PebblesLinkRow(title: "My glyphs", line: "The 12 marks you carved.", chip: .icon(.drawDotted))
                PebblesLinkRow(title: "Owned glyphs", line: "The 5 you collected from others.",
                               chip: .icon(.pouch), done: true)
            })
        }

        @Test(arguments: ReferenceScheme.allCases)
        func linkRows(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(Self.linkRows(), named: "linkRows", config: ReferenceConfig(scheme: scheme))
        }

        @Test func linkRowsAx2() throws {
            ReferenceImage.assertMatches(Self.linkRows(), named: "linkRows",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }
    }
}
