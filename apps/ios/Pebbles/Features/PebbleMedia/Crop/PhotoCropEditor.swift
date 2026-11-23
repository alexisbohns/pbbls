import SwiftUI
import os

/// The photo editor (spec "Layout"): follows the app theme, top bar, canvas,
/// the current tool's panel, then the tool group in thumb reach. Reframe and
/// Rotate edit the crop; Filter and Adjust show the crop with its look
/// (spec 2026-11-23-ios-photo-filters-design).
struct PhotoCropEditor: View {
    @State private var model: PhotoCropModel
    @State private var look: PhotoLookModel
    @State private var isRendering = false
    @State private var renderTask: Task<Void, Never>?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let onCancel: () -> Void
    let onDone: (CGImage, Date?) -> Void

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "crop-editor")

    init(source: CropSource,
         initialTool: CropTool = .ratio,
         look: PhotoLookModel? = nil,
         onCancel: @escaping () -> Void,
         onDone: @escaping (CGImage, Date?) -> Void) {
        let model = PhotoCropModel(source: source)
        model.tool = initialTool
        _model = State(initialValue: model)
        _look = State(initialValue: look ?? PhotoLookModel())
        self.onCancel = onCancel
        self.onDone = onDone
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Group {
                if model.tool.showsLook {
                    LookCanvas(model: look)
                } else {
                    CropCanvas(model: model)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .disabled(isRendering)
            toolPanel
                .frame(height: model.tool.panelHeight)
                .padding(.horizontal, Spacing.lg)
                .disabled(isRendering)
            tabs
                .padding(.bottom, Spacing.sm)
                .disabled(isRendering)
        }
        .background(Color.pebbles.surface)
        .pebblesScreen()
        .task(id: model.tool) { await prepareLookIfNeeded() }
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
            if showsReset {
                Button(action: resetCurrentTool) {
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
        case .filter:
            FilterToolPanel(model: look)
        case .adjust:
            AdjustToolPanel(model: look)
        }
    }

    private var showsReset: Bool {
        switch model.tool {
        case .ratio, .rotate: model.hasChanges
        case .filter: look.look.hasFilterChanges
        case .adjust: look.look.hasAdjustChanges
        }
    }

    /// Reset acts on the open tab's tool only (spec 2026-11-23 decision 8).
    private func resetCurrentTool() {
        animated {
            switch model.tool {
            case .ratio, .rotate: model.reset()
            case .filter: look.look.resetFilter()
            case .adjust: look.look.resetAdjust()
            }
        }
    }

    /// The look works on the crop, so a crop change since the last visit re-prepares it.
    private func prepareLookIfNeeded() async {
        let geometry = model.geometry
        guard model.tool.showsLook, look.preparedFor != geometry else { return }
        do {
            let cropped = try await model.render()
            await look.prepare(cropped, for: geometry)
        } catch {
            logger.error("look crop failed: \(String(describing: error), privacy: .public)")
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
                let cropped = try await model.render()
                let image = await look.apply(to: cropped, sameCrop: look.preparedFor == model.geometry)
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

private extension CropTool {
    var showsLook: Bool { self == .filter || self == .adjust }
    /// As little as each tool needs, so the photo stays large enough to judge the
    /// look: the crop tools fit one row, Filter its presets and knobs, Adjust one
    /// scrolling row of knobs.
    var panelHeight: CGFloat {
        switch self {
        case .ratio, .rotate: 96
        case .filter: 204
        case .adjust: 124
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
