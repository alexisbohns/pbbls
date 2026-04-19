import SwiftUI

/// The drawing surface. Pure UI — owns no persistence, only the in-progress
/// stroke buffer. The host (`GlyphCarveSheet`) owns the committed strokes
/// array and receives a freshly-simplified `GlyphStroke` via `onStrokeCommit`.
///
/// Coordinate system: the visible canvas is `side × side` points; strokes are
/// serialized to the 200x200 SVG coordinate space. The scale factor is applied
/// when converting to `GlyphStroke.d`.
struct GlyphCanvasView: View {
    let committedStrokes: [GlyphStroke]
    let onStrokeCommit: (GlyphStroke) -> Void
    var side: CGFloat = 280
    var strokeColor: Color = .primary

    @State private var activePoints: [CGPoint] = []

    private static let epsilon = 1.5
    private static let storedWidth = 6.0

    var body: some View {
        Canvas { ctx, size in
            // Already-committed strokes
            let scale = size.width / 200.0
            for stroke in committedStrokes {
                var path = SVGPath.path(from: stroke.d)
                path = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                ctx.stroke(
                    path,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: stroke.width * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }

            // In-progress stroke
            if activePoints.count > 1 {
                var livePath = Path()
                livePath.move(to: activePoints[0])
                for point in activePoints.dropFirst() {
                    livePath.addLine(to: point)
                }
                ctx.stroke(
                    livePath,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: Self.storedWidth * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
        }
        .frame(width: side, height: side)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .onChanged { value in
                    let clamped = CGPoint(
                        x: max(0, min(side, value.location.x)),
                        y: max(0, min(side, value.location.y))
                    )
                    activePoints.append(clamped)
                }
                .onEnded { _ in commit() }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Drawing canvas")
        .accessibilityAddTraits(.allowsDirectInteraction)
        .accessibilityValue("\(committedStrokes.count) strokes drawn")
    }

    private func commit() {
        defer { activePoints = [] }
        guard activePoints.count >= 1 else { return }

        let simplified = PathSimplification.simplify(points: activePoints, epsilon: Self.epsilon)
        let scale = 200.0 / Double(side)
        let scaled = simplified.map { CGPoint(x: Double($0.x) * scale, y: Double($0.y) * scale) }
        // swiftlint:disable:next identifier_name
        let d = SVGPath.svgPathString(from: scaled)
        guard !d.isEmpty else { return }

        onStrokeCommit(GlyphStroke(d: d, width: Self.storedWidth))
    }
}

#Preview {
    GlyphCanvasView(
        committedStrokes: [],
        onStrokeCommit: { _ in }
    )
    .padding()
}
