import SwiftUI

/// The chosen people as pills in one scrolling line (souls spec S2), each with
/// a close that removes it, beside the Done / Skip button. The right-edge fade
/// says there is more. From the accessibility sizes the line sits above the
/// button instead (S8). Nothing is drawn in browse mode.
struct SoulPillTray: View {
    let selected: [SoulWithGlyph]
    let label: SoulChooserModel.ButtonLabel
    let onRemove: (UUID) -> Void
    let onConfirm: () -> Void
    /// Reference renders only: `ImageRenderer` draws neither ScrollView content nor TextFields.
    var scrolls = true

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if typeSize.isAccessibilitySize {
            VStack(alignment: .trailing, spacing: Spacing.sm) {
                if !selected.isEmpty {
                    line.frame(maxWidth: .infinity, alignment: .leading)
                }
                button
            }
            .frame(minHeight: 48)
        } else {
            HStack(spacing: Spacing.sm) {
                if selected.isEmpty {
                    Spacer(minLength: 0)
                } else {
                    line
                }
                button
            }
            .frame(minHeight: 48)
        }
    }

    /// The pills, scrolling under a trailing fade; laid out flat for the renders.
    @ViewBuilder
    private var line: some View {
        if scrolls {
            ScrollView(.horizontal, showsIndicators: false) {
                pills
            }
            .contentMargins(.trailing, 28, for: .scrollContent)
            .mask(
                LinearGradient(
                    stops: [
                        .init(color: Color.pebbles.onSurface, location: 0.86),
                        .init(color: .clear, location: 1)
                    ],
                    startPoint: .leading, endPoint: .trailing
                )
            )
        } else {
            HStack(spacing: 0) {
                pills
                Spacer(minLength: 0)
            }
        }
    }

    private var pills: some View {
        HStack(spacing: 6) {
            ForEach(selected) { soul in pill(soul) }
        }
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
