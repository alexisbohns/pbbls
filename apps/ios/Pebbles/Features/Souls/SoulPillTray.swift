import SwiftUI

/// The chosen people as pills in one scrolling line (souls spec S2), each with
/// a close that removes it, beside the Done / Skip button. The right-edge fade
/// says there is more. Nothing is drawn in browse mode.
struct SoulPillTray: View {
    let selected: [SoulWithGlyph]
    let label: SoulChooserModel.ButtonLabel
    let onRemove: (UUID) -> Void
    let onConfirm: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            if !selected.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(selected) { soul in pill(soul) }
                    }
                }
                .contentMargins(.trailing, 28, for: .scrollContent)
                .mask(
                    LinearGradient(stops: [.init(color: .black, location: 0.86), .init(color: .clear, location: 1)],
                                   startPoint: .leading, endPoint: .trailing)
                )
            } else {
                Spacer(minLength: 0)
            }
            button
        }
        .frame(minHeight: 48)
    }

    private func pill(_ soul: SoulWithGlyph) -> some View {
        Button { onRemove(soul.id) } label: {
            HStack(spacing: Spacing.xs + 2) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 24, strokeColor: Color.pebbles.onSurface)
                Text(verbatim: soul.name)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                Image(systemName: "xmark")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .padding(.leading, 7)
            .padding(.trailing, 8)
            .frame(minHeight: 44)
            .background(Capsule().fill(Color.pebbles.primary.opacity(0.20)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Remove \(soul.name)"))
    }

    @ViewBuilder
    private var button: some View {
        switch label {
        case .done(let count):
            Button(action: onConfirm) { Text("Done · \(count)") }
                .buttonStyle(SoulTrayButtonStyle(outlined: false))
        case .doneEmpty:
            Button(action: onConfirm) { Text("Done") }
                .buttonStyle(SoulTrayButtonStyle(outlined: false))
        case .skip:
            Button(action: onConfirm) { Text("Skip") }
                .buttonStyle(SoulTrayButtonStyle(outlined: true))
        }
    }
}

/// A 48 pt pill that hugs its label: filled for Done, outlined for Skip.
struct SoulTrayButtonStyle: ButtonStyle {
    let outlined: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .pebblesFont(.buttonLabel)
            .foregroundStyle(outlined ? Color.pebbles.primary : Color.pebbles.onPrimary)
            .padding(.horizontal, Spacing.xl)
            .frame(height: 48)
            .background(Capsule().fill(outlined ? Color.clear : Color.pebbles.primary))
            .overlay(Capsule().strokeBorder(Color.pebbles.primary, lineWidth: outlined ? 1.5 : 0))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}
