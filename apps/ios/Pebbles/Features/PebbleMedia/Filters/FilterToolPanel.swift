import SwiftUI

/// The Filter tool (spec 2026-11-23 "Layout"): Original and the seven presets as
/// thumbnails, then intensity and the three extras.
struct FilterToolPanel: View {
    @Bindable var model: PhotoLookModel

    private static let tileEdge: CGFloat = 64
    private static let radius: CGFloat = 12

    var body: some View {
        // Scrolls only when larger text makes the sliders outgrow the panel.
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: Spacing.md) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        tile(nil)
                        ForEach(FilterPreset.allCases) { tile($0) }
                    }
                }
                LookSlider(label: "Intensity", value: $model.look.intensity, range: 0...1, defaultValue: 1,
                           readout: .percent, isEnabled: model.look.preset != nil)
                ForEach(PhotoLook.Extra.allCases, id: \.self) { extra in
                    LookSlider(label: extra.label, value: binding(for: extra), range: 0...1,
                               defaultValue: model.look.preset == nil ? 0 : extra.defaultValue, readout: .percent)
                }
            }
            .padding(.vertical, Spacing.sm)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private func binding(for extra: PhotoLook.Extra) -> Binding<Double> {
        Binding(get: { model.look.value(of: extra) }, set: { model.look.set(extra, to: $0) })
    }

    private func tile(_ preset: FilterPreset?) -> some View {
        let selected = model.look.preset == preset
        let image: CGImage? = if let preset { model.thumbnails[preset] } else { model.originalThumbnail }
        let name: LocalizedStringResource = preset?.name ?? "Original"
        let shape = RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
        return Button { model.look.select(preset) } label: {
            VStack(spacing: Spacing.xs) {
                ZStack {
                    shape.fill(Color.pebbles.surfaceContainerHigh)
                    if let image {
                        Image(decorative: image, scale: 1).resizable().scaledToFill()
                    }
                }
                .frame(width: Self.tileEdge, height: Self.tileEdge)
                .clipShape(shape)
                .overlay(shape.strokeBorder(Color.pebbles.primary, lineWidth: selected ? 3 : 0))
                Text(name)
                    .pebblesFont(.meta)
                    .lineLimit(1)
                    .foregroundStyle(selected ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant)
            }
            .frame(width: Self.tileEdge + Spacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(name))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
