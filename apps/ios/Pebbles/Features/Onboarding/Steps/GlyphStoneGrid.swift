import SwiftUI

/// Which glyphs the starter stone shows and how each is spoken. Pure, so the order is tested.
enum GlyphStoneGridLayout {
    static let columns = 3
    static let rows = 3
    static let foldedColumns = 4
    /// The glyph pieces: the stone's cells less the Draw piece.
    static let glyphCount = rows * columns - 1
    static let foldedGlyphCount = foldedColumns - 1

    /// The chosen glyph first, so it is never cut off once carved ones push past eight; then the rest,
    /// padded with nil so the stone keeps its cut while glyphs load; the Draw piece (nil) last.
    static func shown(glyphs: [Glyph], selectedId: UUID?, folded: Bool) -> [Glyph?] {
        let chosen = glyphs.first { $0.id == selectedId }
        let ordered = (chosen.map { [$0] } ?? []) + glyphs.filter { $0.id != selectedId }
        let count = folded ? foldedGlyphCount : glyphCount
        let visible: [Glyph?] = ordered.prefix(count).map { $0 }
        return visible + Array(repeating: nil, count: count - visible.count) + [nil]
    }

    /// What VoiceOver may read for a glyph: a system glyph's name is an internal key ("domain:family"),
    /// never copy, so it has none and is read as its number.
    static func spokenName(_ glyph: Glyph) -> String? {
        glyph.userId == nil ? nil : glyph.name
    }
}

/// The 3 × 3 split stone of glyphs (#1510 D7): eight glyphs and a "Draw" piece marked by a dashed inset.
/// The chosen piece is `primaryContainer`, the rest `surfaceContainer`, no outlines. `folded` is step
/// 4's keyboard-up state: one row of four. The seeds are the same on every surface.
struct GlyphStoneGrid: View {
    let glyphs: [Glyph]
    let selectedId: UUID?
    let onSelect: (Glyph) -> Void
    let onDraw: () -> Void
    var folded = false
    var drawLabel: LocalizedStringResource = "Draw my own"
    var shortDrawLabel: LocalizedStringResource = "Draw"

    /// 12 pt regular, set on the rank itself: a modifier outside a rank loses to it.
    private static let captionRank = SpineType.Style.inclusive(12, 400)

    private var shown: [Glyph?] {
        GlyphStoneGridLayout.shown(glyphs: glyphs, selectedId: selectedId, folded: folded)
    }

    var body: some View {
        let shown = shown
        StoneGrid(
            rows: folded ? 1 : GlyphStoneGridLayout.rows,
            columns: folded ? GlyphStoneGridLayout.foldedColumns : GlyphStoneGridLayout.columns,
            seed: folded ? "onboarding-glyphs-folded" : "onboarding-glyphs",
            fills: shown.map { fill(for: $0) },
            minCellHeight: folded ? 84 : 112
        ) { index, _ in
            cell(shown[index], index: index, isDraw: index == shown.count - 1)
        }
    }

    private func isChosen(_ glyph: Glyph?) -> Bool {
        glyph != nil && glyph?.id == selectedId
    }

    private func fill(for glyph: Glyph?) -> Color {
        isChosen(glyph) ? Color.pebbles.primaryContainer : Color.pebbles.surfaceContainer
    }

    @ViewBuilder
    private func cell(_ glyph: Glyph?, index: Int, isDraw: Bool) -> some View {
        if isDraw {
            Button(action: onDraw) {
                drawPiece
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(drawLabel))
        } else {
            let chosen = isChosen(glyph)
            Button {
                if let glyph { onSelect(glyph) }
            } label: {
                ZStack {
                    if let glyph {
                        GlyphThumbnail(
                            strokes: glyph.strokes,
                            side: folded ? 44 : 56,
                            strokeColor: chosen ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(glyph == nil)
            .accessibilityLabel(label(for: glyph, index: index))
            .accessibilityAddTraits(chosen ? .isSelected : [])
            .accessibilityHidden(glyph == nil)
        }
    }

    private func label(for glyph: Glyph?, index: Int) -> Text {
        guard let glyph else { return Text("") }
        if let name = GlyphStoneGridLayout.spokenName(glyph) { return Text(verbatim: name) }
        return Text("Glyph \(index + 1)")
    }

    /// A dashed `outline` inset marks Draw: 1.5 pt, dash 6, radius 20, `Spacing.sm` in from the piece.
    private var drawPiece: some View {
        VStack(spacing: Spacing.xs) {
            PbblsGlyphView(.plusCircle, folded ? .row : .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
            Text(folded ? shortDrawLabel : drawLabel)
                .spineType(Self.captionRank)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .padding(Spacing.sm)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1.5, dash: [6, 6]))
        }
        .padding(Spacing.sm)
    }
}
