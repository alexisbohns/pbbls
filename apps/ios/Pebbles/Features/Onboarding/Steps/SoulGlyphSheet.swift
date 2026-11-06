import SwiftUI

/// S2 (#1510 §1): the glyph picker for a soul. The person's piece and name in the header, the same
/// split stone as step 4 with "Draw one" as its last piece, and a full-width "Use for <name>". The
/// pick is the sheet's until Use; dismissing keeps the old glyph.
struct SoulGlyphSheet: View {
    let personName: String
    let glyphs: [Glyph]
    let currentGlyphId: UUID?
    let onUse: (UUID) -> Void
    let onDraw: () -> Void

    @State private var picked: UUID?

    init(personName: String, glyphs: [Glyph], currentGlyphId: UUID?,
         onUse: @escaping (UUID) -> Void, onDraw: @escaping () -> Void) {
        self.personName = personName
        self.glyphs = glyphs
        self.currentGlyphId = currentGlyphId
        self.onUse = onUse
        self.onDraw = onDraw
        _picked = State(initialValue: currentGlyphId)
    }

    private var shown: Glyph? { glyphs.first { $0.id == picked } ?? glyphs.first }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            header
            GlyphStoneGrid(
                glyphs: glyphs,
                selectedId: picked,
                onSelect: { picked = $0.id },
                onDraw: onDraw,
                drawLabel: "Draw one"
            )
            Spacer(minLength: Spacing.lg)
            Button { if let picked { onUse(picked) } } label: { Text("Use for \(personName)") }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(picked == nil)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.xl)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.pebbles.surface.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private var header: some View {
        HStack(spacing: Spacing.md) {
            ZStack {
                StoneShape(seed: "onboarding-glyph-sheet-header")
                    .fill(Color.pebbles.primaryContainer)
                if let glyph = shown {
                    GlyphThumbnail(strokes: glyph.strokes, side: 30, strokeColor: Color.pebbles.onPrimaryContainer)
                }
            }
            .frame(width: 48, height: 48)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("A glyph for \(personName)")
                    .spineType(.communityTitle)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .accessibilityAddTraits(.isHeader)
                Text("It marks \(personName) on every pebble.")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }
}
