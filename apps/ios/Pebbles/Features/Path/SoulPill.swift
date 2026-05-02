import SwiftUI

/// Bordered pill used to render a soul in a 3-column grid. Shared between
/// `PebbleReadView.soulsRow` and `SelectedSoulsRow` (the edit form), so the
/// two surfaces stay visually identical. Pure UI: tap behavior is owned by
/// the parent.
///
/// Visual: rounded-rectangle border, no fill. Glyph drawn in
/// `pebblesAccent` with no inner background. Name label in muted
/// foreground, single line, truncates with tail ellipsis.
struct SoulPill: View {
    let glyph: Glyph
    let name: String

    var body: some View {
        HStack(spacing: 8) {
            GlyphThumbnail(
                strokes: glyph.strokes,
                side: 24,
                strokeColor: Color.pebblesAccent,
                backgroundColor: .clear
            )
            .accessibilityHidden(true)

            Text(name)
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(height: 44)
        .frame(maxWidth: .infinity)
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.pebblesAccent.opacity(0.25), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(name)
    }
}

/// Trailing dashed-border pill that opens `SoulPickerSheet`. Used only in
/// the edit form — the read view has no add affordance.
struct AddSoulPill: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "person.badge.plus")
                .font(.callout)
                .foregroundStyle(Color.pebblesMutedForeground)
                .frame(width: 24, height: 24)
                .accessibilityHidden(true)

            Text("Add")
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)
                .lineLimit(1)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(height: 44)
        .frame(maxWidth: .infinity)
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1.5, dash: [4])
                )
        }
        .accessibilityLabel("Add a soul")
    }
}

/// Three-column adaptive grid spec used by both `PebbleReadView.soulsRow`
/// and `SelectedSoulsRow`. Lifted out so the two surfaces share the
/// exact same column geometry.
enum SoulPillGrid {
    static let columns: [GridItem] = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible())
    ]
    static let spacing: CGFloat = 12
}
