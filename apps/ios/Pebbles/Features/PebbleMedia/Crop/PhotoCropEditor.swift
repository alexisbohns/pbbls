import SwiftUI
import os

/// The crop editor (spec "Layout"): follows the app theme, top bar, canvas,
/// the current tool's panel, then the tool tabs in thumb reach. Filters will
/// be a third `CropTool` and tab.
struct PhotoCropEditor: View {
    @State private var model: PhotoCropModel
    @State private var isRendering = false
    let onCancel: () -> Void
    let onDone: (CGImage, Date?) -> Void

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "crop-editor")

    init(source: CropSource,
         initialTool: CropTool = .ratio,
         onCancel: @escaping () -> Void,
         onDone: @escaping (CGImage, Date?) -> Void) {
        let model = PhotoCropModel(source: source)
        model.tool = initialTool
        _model = State(initialValue: model)
        self.onCancel = onCancel
        self.onDone = onDone
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            CropCanvas(model: model)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            toolPanel
                .frame(height: 96)
                .padding(.horizontal, Spacing.lg)
            tabs
                .padding(.bottom, Spacing.sm)
        }
        .background(Color.pebbles.surface)
        .pebblesScreen()
    }

    private var topBar: some View {
        HStack {
            Button(action: onCancel) {
                Text("Cancel")
                    .pebblesFont(.body)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
            }
            .buttonStyle(.plain)
            Spacer()
            if model.hasChanges {
                Button { withAnimation(.snappy) { model.reset() } } label: {
                    Text("Reset")
                        .pebblesFont(.body)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Button { Task { await done() } } label: {
                Text("Done")
                    .pebblesFont(.bodyEmphasized)
                    .foregroundStyle(Color.pebbles.primary)
                    .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
            }
            .buttonStyle(.plain)
            .disabled(isRendering)
        }
        .padding(.horizontal, Spacing.lg)
        .frame(height: 52)
    }

    @ViewBuilder
    private var toolPanel: some View {
        switch model.tool {
        case .ratio:
            HStack(spacing: Spacing.sm) {
                ForEach(CropRatio.allCases) { ratio in
                    CropRatioChip(ratio: ratio, isSelected: model.geometry.ratio == ratio) {
                        withAnimation(.snappy) { model.select(ratio) }
                    }
                }
            }
            .frame(maxWidth: .infinity)
        case .rotate:
            HStack(spacing: Spacing.md) {
                Button { withAnimation(.snappy) { model.turnQuarter() } } label: {
                    PbblsGlyphView(.againBackward, size: 22, style: AnyShapeStyle(Color.pebbles.onSurface))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Rotate 90°"))
                AngleRuler(angle: model.geometry.angle,
                           onChange: { model.setAngle($0) },
                           onEditingChanged: { model.isRotating = $0 })
            }
        }
    }

    private var tabs: some View {
        HStack(spacing: Spacing.xxl) {
            tab(.ratio, glyph: .rectangleDashed, label: "Ratio")
            tab(.rotate, glyph: .setSquare, label: "Rotate")
        }
        .frame(maxWidth: .infinity)
    }

    private func tab(_ tool: CropTool, glyph: PbblsGlyph, label: LocalizedStringResource) -> some View {
        let selected = model.tool == tool
        let colour = selected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant
        return Button { model.tool = tool } label: {
            VStack(spacing: Spacing.xs) {
                PbblsGlyphView(glyph, size: 24, style: AnyShapeStyle(colour))
                Text(label).pebblesFont(.captionEmphasized).foregroundStyle(colour)
            }
            .frame(minWidth: 64, minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func done() async {
        isRendering = true
        defer { isRendering = false }
        do {
            let image = try await model.render()
            onDone(image, model.source.captureDate)
        } catch {
            logger.error("crop render failed: \(String(describing: error), privacy: .public)")
        }
    }
}

#Preview {
    // A gradient stand-in for a photo, drawn in role colours.
    let side = 768
    let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side * 4 / 3)).image { ctx in
        let colours = [UIColor.pebbles(.primaryContainer).cgColor, UIColor.pebbles(.primary).cgColor] as CFArray
        if let gradient = CGGradient(colorsSpace: nil, colors: colours, locations: [0, 1]) {
            ctx.cgContext.drawLinearGradient(gradient, start: .zero,
                                             end: CGPoint(x: side, y: side * 4 / 3), options: [])
        }
    }
    if let cgImage = image.cgImage {
        PhotoCropEditor(source: CropSource(image: cgImage, captureDate: nil), onCancel: {}, onDone: { _, _ in })
    }
}
