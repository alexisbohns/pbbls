import SwiftUI

/// A people line set from its parts as one wrapping `Text` (#1130 R7): the
/// entry's line ("with [g] MARA, [g] ELIO & twelve others …") and the card's
/// strip. Names are ink, the rest ink-2; each run takes its rank as a span.
struct PeopleLineView: View {
    let parts: [PeopleLinePart]
    let glyphs: [String: [GlyphStroke]]
    let markSide: CGFloat
    let base: SpineType.Style
    let name: SpineType.Style
    let aside: SpineType.Style
    let more: SpineType.Style
    var alignment: TextAlignment = .leading

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        line
            .multilineTextAlignment(alignment)
            .spineType(base)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private var line: Text {
        var text = Text(verbatim: "")
        for (index, part) in parts.enumerated() {
            let next = parts.indices.contains(index + 1) ? parts[index + 1] : nil
            text = text + run(part) + Text(verbatim: gap(after: part, before: next))
        }
        return text
    }

    private func run(_ part: PeopleLinePart) -> Text {
        switch part {
        case .aside(let words):
            return Text(verbatim: words).spineSpan(aside, dynamicTypeSize: dynamicTypeSize,
                                                   legibilityWeight: legibilityWeight)
        case .separator(let mark):
            return Text(verbatim: mark)
        case .mark(let id):
            let image = SoulMark.image(strokes: glyphs[id], side: markSide, scheme: scheme, scale: displayScale)
            return Text(image).baselineOffset(-markSide * 0.18)
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
