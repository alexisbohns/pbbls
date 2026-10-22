import SwiftUI

/// A thumbnail of the face the turn leads to (#1146 §Footer, the Label
/// exploration), tilted −7°: to the pebble, a 15 × 22 stock card with an inner
/// rule and a 9 × 7 stone in the emotion's spot at 45 % height; to the moment,
/// a photo block at 35 % opacity down to 40 % and three legend lines. 1 pt ink
/// outline. Decorative and unscaled: the turn button carries the label.
struct FaceMiniature: View {
    let to: CardFace
    let stone: Color

    var body: some View {
        Canvas { context, size in
            let card = CGSize(width: 15, height: 22)
            let origin = CGPoint(x: (size.width - card.width) / 2, y: (size.height - card.height) / 2)
            let rect = CGRect(origin: origin, size: card)
            let shape = RoundedRectangle(cornerRadius: 2, style: .continuous).path(in: rect)
            context.fill(shape, with: .color(Color.pebbles.stock))
            context.stroke(shape, with: .color(Color.pebbles.onSurface), lineWidth: 1)
            switch to {
            case .verso:
                let rule = RoundedRectangle(cornerRadius: 1).path(in: rect.insetBy(dx: 2, dy: 2))
                context.stroke(rule, with: .color(Color.pebbles.onSurface), lineWidth: 0.8)
                let centre = CGPoint(x: rect.midX, y: rect.minY + rect.height * 0.45)
                context.fill(Path(ellipseIn: CGRect(x: centre.x - 4.5, y: centre.y - 3.5, width: 9, height: 7)),
                             with: .color(stone))
            case .recto:
                let photo = CGRect(x: rect.minX + 2.3, y: rect.minY + 2.3, width: card.width - 4.6,
                                   height: card.height * 0.4 - 2.3)
                context.fill(Path(photo), with: .color(Color.pebbles.onSurface.opacity(0.35)))
                for (y, end) in [(13.0, 12.0), (15.8, 10.2), (18.6, 11.2)] {
                    var line = Path()
                    line.move(to: CGPoint(x: rect.minX + 3, y: rect.minY + y))
                    line.addLine(to: CGPoint(x: rect.minX + end, y: rect.minY + y))
                    context.stroke(line, with: .color(Color.pebbles.onSurface.opacity(0.6)),
                                   style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
                }
            }
        }
        .frame(width: CardLayout.miniature.width, height: CardLayout.miniature.height)
        .rotationEffect(.degrees(CardLayout.miniatureTilt))
        .accessibilityHidden(true)
    }
}
