import SwiftUI

/// One A–Z row (souls spec S1–S2): the drawing, the name in regular type, the
/// pebble count, then a plus or a tick. In browse mode the trailing control is
/// a chevron and the row opens the person.
struct SoulRow: View {
    enum Trailing { case plus, tick, chevron }

    let soul: SoulWithGlyph
    let trailing: Trailing
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 30, strokeColor: Color.pebbles.onSurfaceVariant)
                    .accessibilityHidden(true)
                Text(verbatim: soul.name)
                    .pebblesFont(trailing == .tick ? .bodyEmphasized : .body)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Spacer(minLength: Spacing.sm)
                HStack(spacing: Spacing.xs) {
                    PbblsGlyphView(.pebbleRound, size: 13, relativeTo: .footnote,
                                   style: AnyShapeStyle(Color.pebbles.outline))
                    Text(verbatim: "\(soul.pebblesCount)")
                        .pebblesFont(.subhead)
                        .monospacedDigit()
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                control
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: soul.name))
        .accessibilityValue(Text("\(soul.pebblesCount) pebbles"))
        .accessibilityAddTraits(trailing == .tick ? [.isButton, .isSelected] : [.isButton])
    }

    @ViewBuilder
    private var control: some View {
        switch trailing {
        case .plus:
            PbblsGlyphView(.plusCircle, size: 26, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
        case .tick:
            PbblsGlyphView(.checkCircle, size: 26, style: AnyShapeStyle(Color.pebbles.primary))
        case .chevron:
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.pebbles.outline)
                .accessibilityHidden(true)
        }
    }
}
