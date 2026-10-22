import SwiftUI

/// A people line set from its parts as one wrapping `Text` (#1130 R7): the
/// entry's line ("with [g] MARA, [g] ELIO & twelve others …") and the card's
/// strip. Names are ink, the rest ink-2; each run takes its rank as a span.
struct PeopleLineView: View {
    let parts: [PeopleLinePart]
    let glyphs: [String: [GlyphStroke]]
    /// The mark's side at the base rank's nominal size; it grows with the rank's
    /// resolved font (Dynamic Type, and the card's ×1.3 cap), as Android's em-sized marks do.
    let markSide: CGFloat
    let base: SpineType.Style
    let name: SpineType.Style
    let aside: SpineType.Style
    let more: SpineType.Style
    var alignment: TextAlignment = .leading

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(\.displayScale) private var displayScale
    /// Resolves the marks' inks for this scheme and contrast before they are drawn to images.
    @Environment(\.self) private var environment

    var body: some View {
        line
            .multilineTextAlignment(alignment)
            .spineType(base)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    /// The mark's side, from the base rank's resolved font: its size over the nominal one.
    private var resolvedMarkSide: CGFloat {
        let resolved = SpineType.resolve(base, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return base.size > 0 ? markSide * resolved.size / base.size : markSide
    }

    private var line: Text {
        let side = resolvedMarkSide
        let inks = SoulMark.Inks(
            glyph: Color.pebbles.onSurfaceVariant.resolve(in: environment),
            placeholder: Color.pebbles.outline.resolve(in: environment)
        )
        // `Text + Text`, not interpolation: an interpolated `Text` is a localised key,
        // and the build extracts it ("%@%@%@") into the string catalogue.
        var text = Text(verbatim: "")
        for (index, part) in parts.enumerated() {
            let next = parts.indices.contains(index + 1) ? parts[index + 1] : nil
            text = text + run(part, side: side, inks: inks) + Text(verbatim: gap(after: part, before: next))
        }
        return text
    }

    private func run(_ part: PeopleLinePart, side: CGFloat, inks: SoulMark.Inks) -> Text {
        switch part {
        case .aside(let words):
            return Text(verbatim: words).spineSpan(aside, dynamicTypeSize: dynamicTypeSize,
                                                   legibilityWeight: legibilityWeight)
        case .separator(let mark):
            return Text(verbatim: mark)
        case .mark(let id):
            let image = SoulMark.image(strokes: glyphs[id], side: side, inks: inks, scale: displayScale)
            return Text(image).baselineOffset(-side * 0.18)
        case .name(let words):
            return Text(verbatim: words)
                .spineSpan(name, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
                .foregroundStyle(Color.pebbles.onSurface)
        case .more(let words):
            return Text(verbatim: words).spineSpan(more, dynamicTypeSize: dynamicTypeSize,
                                                   legibilityWeight: legibilityWeight)
        }
    }

    /// One space between runs; none before a comma; a thin space between a mark and its name.
    private func gap(after part: PeopleLinePart, before next: PeopleLinePart?) -> String {
        guard let next else { return "" }
        if case .separator = next { return "" }
        if case .mark = part, case .name = next { return "\u{2009}" }
        return " "
    }
}
