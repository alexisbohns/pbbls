import SwiftUI

/// A card's field (#1130 R5): the stock, the spot tint over it, and the
/// valence pattern: rays from the stone's corner for a highlight (every 2.2°,
/// from −6 % −14 %), ripples for a neutral (every 6.3 pt, from −4 % −10 %),
/// contours from the far corner for a lowlight (150 % × 110 % ellipses at
/// 112 % 122 %, every 6.8 pt). A `Canvas` does not clip its drawing to its
/// frame, so the context is clipped to its own rect: a drawing clip, not a view
/// one, so hit-testing and the overhangs D4 protects are untouched.
struct SpineCardField: View {
    let polarity: ValencePolarity
    let spot: Color
    let tint: Double
    let pattern: Double
    let size: CGSize

    var body: some View {
        Canvas { context, canvas in
            let rect = CGRect(origin: .zero, size: canvas)
            context.clip(to: Path(rect))
            context.fill(Path(rect), with: .color(Color.pebbles.stock))
            context.fill(Path(rect), with: .color(spot.opacity(tint)))
            let ink = GraphicsContext.Shading.color(spot.opacity(pattern))
            switch polarity {
            case .highlight: Self.rays(in: canvas, context: context, ink: ink)
            case .neutral: Self.ripples(in: canvas, context: context, ink: ink)
            case .lowlight: Self.contours(in: canvas, context: context, ink: ink)
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityHidden(true)
    }

    private static func rays(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let origin = CGPoint(x: -0.06 * size.width, y: -0.14 * size.height)
        let length = hypot(size.width, size.height) * 1.5
        var path = Path()
        for degrees in stride(from: 0.0, through: 90.0, by: 2.2) {
            let angle = CGFloat(degrees) * .pi / 180
            path.move(to: origin)
            path.addLine(to: CGPoint(x: origin.x + length * cos(angle), y: origin.y + length * sin(angle)))
        }
        context.stroke(path, with: ink, lineWidth: 1)
    }

    private static func ripples(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let centre = CGPoint(x: -0.04 * size.width, y: -0.10 * size.height)
        let farthest = hypot(size.width - centre.x, size.height - centre.y)
        var path = Path()
        for radius in stride(from: 5.9, through: farthest, by: 6.3) {
            let diameter = 2 * radius
            path.addEllipse(in: CGRect(x: centre.x - radius, y: centre.y - radius, width: diameter, height: diameter))
        }
        context.stroke(path, with: ink, lineWidth: 0.8)
    }

    private static func contours(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let centre = CGPoint(x: 1.12 * size.width, y: 1.22 * size.height)
        let squash = (1.1 * size.height) / max(1.5 * size.width, 1)
        var path = Path()
        for rx in stride(from: 6.4, through: 2.6 * size.width, by: 6.8) {
            let ry = rx * squash
            path.addEllipse(in: CGRect(x: centre.x - rx, y: centre.y - ry, width: 2 * rx, height: 2 * ry))
        }
        context.stroke(path, with: ink, lineWidth: 0.8)
    }
}
