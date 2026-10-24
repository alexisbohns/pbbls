import SwiftUI

/// How a day dot reads (#1226). `filled` a day present; `ring` a day of the span
/// without it (the web's inset ring at 70 %); `socket` an empty slot on a ring
/// that holds stones (the garland), fainter so the stones carry it; `quiet` a
/// filled dot pushed back, for a day outside the span (a calendar's padding).
enum DotStyle {
    case filled, ring, socket, quiet
}

extension GraphicsContext {
    /// The single primitive behind every dot (Android: `DrawScope.dotMark`).
    func dotMark(center: CGPoint, diameter: CGFloat, style: DotStyle, color: Color,
                 ringWidth: CGFloat = SpineLayout.dotRing) {
        let radius = diameter / 2
        func circle(_ r: CGFloat) -> Path {
            Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: 2 * r, height: 2 * r))
        }
        switch style {
        case .filled:
            fill(circle(radius), with: .color(color))
        case .ring:
            stroke(circle(radius - ringWidth / 2), with: .color(color.opacity(0.7)), lineWidth: ringWidth)
        case .socket:
            stroke(circle(radius - ringWidth / 2), with: .color(color.opacity(0.4)), lineWidth: ringWidth)
        case .quiet:
            fill(circle(radius), with: .color(color.opacity(0.25)))
        }
    }
}

/// One dot as a view, for a key or a legend. Decorative.
struct DotMark: View {
    let style: DotStyle
    let color: Color
    var diameter: CGFloat = SpineLayout.dot

    var body: some View {
        Canvas { context, size in
            context.dotMark(center: CGPoint(x: size.width / 2, y: size.height / 2),
                            diameter: min(size.width, size.height), style: style, color: color)
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }
}
