import SwiftUI

/// The glint (#1130 R12–R13), clipped to the stone at its material's strength:
/// the still sheen (Reduce Motion), the sweep band crossing from the light, and
/// the four-point spark. The geometry is the web mock's, as fractions of the
/// stone. Everything rests invisible, so the canvas draws nothing at rest.
struct StoneGlint: View {
    let outline: Path
    let stone: CGSize
    let frame: StoneMotion.Frame
    let strength: Double

    var body: some View {
        Canvas { context, size in
            guard frame.stillSheen > 0 || frame.sweep != nil || frame.sparkAlpha > 0 else { return }
            context.clip(to: outline)
            context.opacity = strength
            let width = size.width
            let height = size.height
            if frame.stillSheen > 0 {
                var sheen = context
                sheen.opacity = strength * frame.stillSheen
                sheen.translateBy(x: 0.38 * width, y: 0.3 * height)
                sheen.rotate(by: .degrees(-24))
                sheen.scaleBy(x: 1, y: 0.14 * height / (0.3 * width))
                let radius = 0.3 * width
                sheen.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: 2 * radius, height: 2 * radius)),
                           with: .radialGradient(Self.sheenGlow, center: .zero, startRadius: 0, endRadius: radius))
            }
            if let sweep = frame.sweep {
                var band = context
                band.translateBy(x: width / 2, y: height / 2)
                band.rotate(by: .degrees(22))
                band.translateBy(x: -width / 2 + sweep * StoneMotion.Sweep.travel * width, y: -height / 2)
                let rect = CGRect(x: -0.55 * width, y: -0.4 * height, width: 0.38 * width, height: 1.8 * height)
                band.fill(Path(rect), with: .linearGradient(Self.sweepBand, startPoint: CGPoint(x: rect.minX, y: 0),
                                                            endPoint: CGPoint(x: rect.maxX, y: 0)))
            }
            if frame.sparkAlpha > 0, frame.sparkScale > 0 {
                var spark = context
                spark.translateBy(x: 0.33 * width, y: 0.25 * height)
                spark.rotate(by: .degrees(frame.sparkRotation))
                spark.scaleBy(x: frame.sparkScale, y: frame.sparkScale)
                spark.fill(Self.spark(radius: 0.085 * width),
                           with: .color(StoneGlintInk.light.opacity(frame.sparkAlpha)))
            }
        }
        .frame(width: stone.width, height: stone.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private static let sheenGlow = Gradient(colors: [
        StoneGlintInk.light.opacity(StoneGlintInk.sheenPeak), StoneGlintInk.light.opacity(0)
    ])
    private static let sweepBand = Gradient(colors: [
        StoneGlintInk.light.opacity(0), StoneGlintInk.light.opacity(StoneGlintInk.sweepPeak),
        StoneGlintInk.light.opacity(0)
    ])

    /// The mock's four-point star about the origin: quadratic arms pinched to 16 % of the radius.
    static func spark(radius r: CGFloat) -> Path {
        let k = r * 0.16
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -r))
        path.addQuadCurve(to: CGPoint(x: r, y: 0), control: CGPoint(x: k, y: -k))
        path.addQuadCurve(to: CGPoint(x: 0, y: r), control: CGPoint(x: k, y: k))
        path.addQuadCurve(to: CGPoint(x: -r, y: 0), control: CGPoint(x: -k, y: k))
        path.addQuadCurve(to: CGPoint(x: 0, y: -r), control: CGPoint(x: -k, y: -k))
        path.closeSubpath()
        return path
    }
}
