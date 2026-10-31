import SwiftUI

/// You's Glyphs (#1382 D19): three link rows, each opening Glyphs on its tab.
/// A line counts what the server counted ("The 12 marks you carved."), invites
/// at zero, and stays count-free while the count is absent.
struct YouGlyphsSection: View {
    let carved: Int?
    let owned: Int?

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            PebblesSectionHead(String(localized: "Glyphs"))
            VStack(alignment: .leading, spacing: 0) {
                row(.mine, title: "you.glyphs.mine.title", line: Self.line("you.glyphs.mine", carved, locale: locale),
                    icon: .drawDotted)
                row(.owned, title: "you.glyphs.owned.title",
                    line: Self.line("you.glyphs.owned", owned, locale: locale), icon: .pouch)
                row(.commu, title: "you.glyphs.community.title",
                    line: SpineStrings.string("you.glyphs.community.line", locale: locale), icon: .petroglyph)
            }
        }
    }

    /// The plural line for a count above zero, the invitation at zero, the plain line without one.
    static func line(_ prefix: String, _ count: Int?, locale: Locale) -> String {
        guard let count else { return SpineStrings.string("\(prefix).plain", locale: locale) }
        guard count > 0 else { return SpineStrings.string("\(prefix).none", locale: locale) }
        return SpineStrings.format("\(prefix).line", locale: locale, count)
    }

    private func row(_ tab: GlyphTab, title: String, line: String, icon: PbblsIcon) -> some View {
        NavigationLink(value: YouRoute.glyphTab(tab)) {
            PebblesLinkRow(title: SpineStrings.string(title, locale: locale), line: line, chip: .icon(icon))
        }
        .buttonStyle(.plain)
    }
}
