import SwiftUI

/// Cold-launch splash that draws the bundled Pebbles logo stroke-by-stroke
/// in `Color.pebblesAccent`. Each parsed path animates `.trim(from:0,to:1)`
/// in document order with a small inter-path stagger; `onComplete` fires
/// when the last path finishes.
///
/// Reduce Motion or parse failure → static fallback (`Image("WelcomeLogo")`
/// template-tinted) and `onComplete` is invoked on appear so `RootView`
/// can advance immediately.
struct SplashView: View {
    let onComplete: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: LogoSVGModel?
    @State private var pathProgress: [Double] = []
    @State private var didStart = false

    /// Per-path delay (seconds) — determines stagger between strokes.
    private let stagger: TimeInterval = 0.08
    /// Per-path draw duration (seconds).
    private let drawDuration: TimeInterval = 0.7

    var body: some View {
        ZStack {
            Color.pebblesBackground.ignoresSafeArea()

            Group {
                if let model, !reduceMotion {
                    animatedLogo(model: model)
                } else {
                    fallbackLogo
                }
            }
            .frame(maxWidth: 220, maxHeight: 220)
        }
        .onAppear(perform: start)
    }

    @ViewBuilder
    private func animatedLogo(model: LogoSVGModel) -> some View {
        ZStack {
            ForEach(Array(model.paths.enumerated()), id: \.offset) { index, path in
                LogoPathShape(path: path, viewBox: model.viewBox)
                    .trim(from: 0, to: pathProgress.indices.contains(index) ? pathProgress[index] : 0)
                    .stroke(
                        Color.pebblesAccent,
                        style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round)
                    )
            }
        }
        .accessibilityHidden(true)
    }

    private var fallbackLogo: some View {
        Image("WelcomeLogo")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .foregroundStyle(Color.pebblesAccent)
            .accessibilityHidden(true)
    }

    private func start() {
        guard !didStart else { return }
        didStart = true

        if model == nil {
            model = LogoSVGModel.loadFromBundle()
        }

        guard let model, !reduceMotion else {
            onComplete()
            return
        }

        pathProgress = Array(repeating: 0, count: model.paths.count)

        for index in model.paths.indices {
            withAnimation(
                .easeOut(duration: drawDuration)
                    .delay(Double(index) * stagger)
            ) {
                pathProgress[index] = 1
            }
        }

        let total = Double(max(model.paths.count - 1, 0)) * stagger + drawDuration
        DispatchQueue.main.asyncAfter(deadline: .now() + total) {
            onComplete()
        }
    }
}

/// Renders a `CGPath` from a logo viewBox into a SwiftUI `Shape`'s drawing
/// rect, preserving aspect ratio and centering inside the rect — same fit
/// math as `LayerShape` in `PebbleAnimatedRenderView`.
private struct LogoPathShape: Shape {
    let path: CGPath
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let offsetX = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let offsetY = (rect.height - scaledHeight) / 2 - viewBox.minY * scale
        var transform = CGAffineTransform(scaleX: scale, y: scale)
            .concatenating(CGAffineTransform(translationX: offsetX, y: offsetY))
        guard let transformed = path.copy(using: &transform) else {
            return Path(path)
        }
        return Path(transformed)
    }
}

#Preview {
    SplashView(onComplete: {})
}
