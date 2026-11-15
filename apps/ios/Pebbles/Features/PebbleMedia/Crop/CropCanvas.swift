import SwiftUI

/// The photo under a fixed window (spec "Canvas"). The image is placed by the
/// same chain the renderer uses — scale, turn, offset — so what is in the
/// window is exactly what Done keeps.
struct CropCanvas: View {
    let model: PhotoCropModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @GestureState private var isGesturing = false

    var body: some View {
        GeometryReader { proxy in
            let window = windowRect(in: proxy.size)
            let g = model.geometry
            let unit = window.height
            ZStack {
                Image(decorative: model.source.image, scale: 1)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: g.imageSize.width * g.scale * unit, height: g.imageSize.height * g.scale * unit)
                    .rotationEffect(.radians(g.theta))
                    .offset(x: g.windowOffset.width * unit, y: g.windowOffset.height * unit)
                    .position(x: window.midX, y: window.midY)
                CropScrim(window: window)
                    .fill(Color.pebbles.surface.opacity(0.78), style: FillStyle(eoFill: true))
                    .allowsHitTesting(false)
                if model.isInteracting || model.isRotating {
                    CropGrid(divisions: model.isRotating ? 6 : 3)
                        .stroke(Color.pebbles.surface.opacity(0.6), lineWidth: 0.75)
                        .frame(width: window.width, height: window.height)
                        .position(x: window.midX, y: window.midY)
                }
                Rectangle()
                    .stroke(Color.pebbles.outlineVariant, lineWidth: 1)
                    .frame(width: window.width, height: window.height)
                    .position(x: window.midX, y: window.midY)
            }
            .contentShape(Rectangle())
            .gesture(gesture(windowHeight: unit))
            .animation(reduceMotion ? nil : .spring(duration: 0.3), value: model.geometry.ratio)
            .onChange(of: isGesturing) { _, active in
                // Also runs when the system cancels the touch, which never reaches `onEnded`.
                if !active { endGesture() }
            }
            .accessibilityElement()
            .accessibilityAddTraits(.isImage)
            .accessibilityLabel(Text("Photo"))
            .accessibilityAction(named: Text("Zoom in")) { model.zoom(by: 1.25) }
            .accessibilityAction(named: Text("Zoom out")) { model.zoom(by: 0.8) }
        }
        .clipped()
    }

    /// The largest window of the current format that fits with a gutter.
    private func windowRect(in size: CGSize) -> CGRect {
        let inset = Spacing.lg
        let available = CGSize(width: max(size.width - inset * 2, 1), height: max(size.height - inset * 2, 1))
        let ratio = model.geometry.ratio.value
        let height = min(available.height, available.width / ratio)
        let width = height * ratio
        return CGRect(x: (size.width - width) / 2, y: (size.height - height) / 2, width: width, height: height)
    }

    private func gesture(windowHeight: CGFloat) -> some Gesture {
        SimultaneousGesture(DragGesture(minimumDistance: 0), MagnifyGesture())
            .updating($isGesturing) { _, state, _ in state = true }
            .onChanged { value in
                model.gestureChanged(translation: value.first?.translation ?? .zero,
                                     magnification: value.second?.magnification ?? 1,
                                     windowHeight: windowHeight)
            }
    }

    private func endGesture() {
        if reduceMotion {
            model.gestureEnded()
        } else {
            withAnimation(.spring(duration: 0.35, bounce: 0.15)) { model.gestureEnded() }
        }
    }
}

/// Lines dividing a rect into `divisions` × `divisions`.
struct CropGrid: Shape {
    let divisions: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        for i in 1..<divisions {
            let x = rect.minX + rect.width * CGFloat(i) / CGFloat(divisions)
            let y = rect.minY + rect.height * CGFloat(i) / CGFloat(divisions)
            path.move(to: CGPoint(x: x, y: rect.minY))
            path.addLine(to: CGPoint(x: x, y: rect.maxY))
            path.move(to: CGPoint(x: rect.minX, y: y))
            path.addLine(to: CGPoint(x: rect.maxX, y: y))
        }
        return path
    }
}

/// Everything but the window, filled even-odd. The window is animatable, so the
/// scrim follows it when the format changes.
struct CropScrim: Shape {
    var window: CGRect

    var animatableData: CGRect.AnimatableData {
        get { window.animatableData }
        set { window.animatableData = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addRect(window)
        return path
    }
}
