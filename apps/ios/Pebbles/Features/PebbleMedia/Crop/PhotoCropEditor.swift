import SwiftUI
import os

/// The crop editor (spec "Layout"): follows the app theme, top bar, canvas,
/// the current tool's panel, then the tool group in thumb reach. Filters will
/// be a third `CropTool` in it.
struct PhotoCropEditor: View {
    @State private var model: PhotoCropModel
    @State private var isRendering = false
    @State private var renderTask: Task<Void, Never>?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
                .disabled(isRendering)
            toolPanel
                .frame(height: 96)
                .padding(.horizontal, Spacing.lg)
                .disabled(isRendering)
            tabs
                .padding(.bottom, Spacing.sm)
                .disabled(isRendering)
        }
        .background(Color.pebbles.surface)
        .pebblesScreen()
    }

    /// A change with the editor's own animation, none under Reduce Motion.
    private func animated(_ change: () -> Void) {
        if reduceMotion {
            change()
        } else {
            withAnimation(.snappy, change)
        }
    }

    private var topBar: some View {
        ZStack {
            HStack {
                Button(action: cancel) {
                    Text("Cancel")
                        .pebblesFont(.body)
                        .lineLimit(1)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                }
                .buttonStyle(.plain)
                Spacer()
                Button(action: done) {
                    Text("Done")
                        .pebblesFont(.bodyEmphasized)
                        .lineLimit(1)
                        .foregroundStyle(Color.pebbles.primary)
                        .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
                }
                .buttonStyle(.plain)
                .disabled(isRendering)
            }
            if model.hasChanges {
                Button { animated { model.reset() } } label: {
                    Text("Reset")
                        .pebblesFont(.body)
                        .lineLimit(1)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .disabled(isRendering)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .frame(minHeight: 52)
        // Chrome, not content: it stops growing before it crowds the three buttons.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    @ViewBuilder
    private var toolPanel: some View {
        switch model.tool {
        case .ratio:
            HStack(spacing: Spacing.sm) {
                ForEach(CropRatio.allCases) { ratio in
                    CropRatioChip(ratio: ratio, isSelected: model.geometry.ratio == ratio) {
                        animated { model.select(ratio) }
                    }
                }
            }
            .frame(maxWidth: .infinity)
        case .rotate:
            HStack(spacing: Spacing.md) {
                Button { animated { model.turnQuarter() } } label: {
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
        CropToolGroup(tools: CropTool.allCases, selection: $model.tool)
            .padding(.horizontal, Spacing.lg)
    }

    /// Cancel wins over a render in flight: its result is dropped.
    private func cancel() {
        renderTask?.cancel()
        renderTask = nil
        onCancel()
    }

    private func done() {
        guard !isRendering else { return }
        isRendering = true
        renderTask = Task {
            do {
                let image = try await model.render()
                guard !Task.isCancelled else { return }
                // `isRendering` stays on: the editor is about to be dismissed and
                // Done must not fire twice meanwhile.
                onDone(image, model.source.captureDate)
            } catch {
                logger.error("crop render failed: \(String(describing: error), privacy: .public)")
                isRendering = false
            }
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
