import SwiftUI

/// The Filter tool (spec 2026-11-23 "Layout"): Original and the seven presets as
/// thumbnails, the selected one a circle, then intensity and the three extras as a
/// row of knobs.
struct FilterToolPanel: View {
    @Bindable var model: PhotoLookModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                HStack(alignment: .top, spacing: 0) {
                    LookKnob(label: "Intensity", value: $model.look.intensity, defaultValue: 1,
                             isEnabled: model.look.preset != nil)
                        .frame(maxWidth: .infinity)
                    ForEach(PhotoLook.Extra.allCases, id: \.self) { extra in
                        LookKnob(label: extra.label, value: binding(for: extra),
                                 defaultValue: model.look.preset == nil ? 0 : extra.defaultValue)
                            .frame(maxWidth: .infinity)
                    }
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
        // The selected tile rounds into a circle: the shape is the selection mark.
        let shape = RoundedRectangle(cornerRadius: selected ? Self.tileEdge / 2 : Self.radius, style: .circular)
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
                Text(name)
                    .pebblesFont(.captionEmphasized)
                    .lineLimit(1)
                    .foregroundStyle(selected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant)
            }
            .frame(width: Self.tileEdge + Spacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(reduceMotion ? nil : .snappy, value: selected)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(name))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
