import SwiftUI

/// A settled week, or the week being lived, whose next stone is drawn dotted.
enum CairnState {
    case settled, live
}

/// A week as a cairn (#1226, D1): one flat stone per day that holds a moment,
/// widest at the base, stacked from the bottom of a 30 × 42 pt box, in one
/// `color` (the caller inks the focused week). Decorative: the strip item says
/// the week in words. Android: `CairnMark`.
struct CairnMark: View {
    let days: [DayWeight]
    let state: CairnState
    let color: Color

    var body: some View {
        let widths = CairnGeometry.widths(days)
        let live = state == .live
        Canvas { context, size in
            guard let stone = FlatStone.medium else { return }
            // A busy week flattens its stones rather than narrowing them.
            let squash = CairnGeometry.squash(widths, aspect: stone.aspect, live: live)
            var bottom = size.height
            for (index, width) in widths.enumerated() {
                let height = CairnGeometry.height(width: width, aspect: stone.aspect) * squash
                if index > 0 { bottom += height * CairnGeometry.overlap }
                context.drawFlatStone(stone, in: CGRect(x: (size.width - width) / 2, y: bottom - height,
                                                        width: width, height: height), color: color)
                bottom -= height
            }
            if live {
                let width = CairnGeometry.nextWidth
                let height = CairnGeometry.height(width: width, aspect: stone.aspect) * squash
                if !widths.isEmpty { bottom += height * CairnGeometry.overlap }
                context.drawFlatStone(stone, in: CGRect(x: (size.width - width) / 2, y: bottom - height,
                                                        width: width, height: height), color: color, dotted: true)
            }
        }
        .frame(width: CairnGeometry.box.width, height: CairnGeometry.box.height)
        .accessibilityHidden(true)
    }
}

/// The medium river-pebble outline the cairns and the dolmen are built from.
struct FlatStone {
    let art: WobbleBackdropArt
    /// Width over height of the outline as drawn.
    let aspect: CGFloat

    static var medium: FlatStone? {
        WobbleRenderer.softBackdropArt(size: .medium, polarity: .neutral).map {
            FlatStone(art: $0, aspect: CGFloat(PebbleOutlineGeometry.aspectRatio(for: .medium)))
        }
    }
}

extension GraphicsContext {
    /// The outline stretched to `rect` (pressed flat when `rect` is shorter than
    /// the outline's aspect): filled, or dotted for the live week's next stone.
    func drawFlatStone(_ stone: FlatStone, in rect: CGRect, color: Color, dotted: Bool = false) {
        let natural = CGSize(width: rect.width, height: rect.width / stone.aspect)
        let outline = WobbledBackdropShape(art: stone.art).path(in: CGRect(origin: .zero, size: natural))
        let transform = CGAffineTransform(translationX: rect.minX, y: rect.minY)
            .scaledBy(x: 1, y: rect.height / natural.height)
        let path = outline.applying(transform)
        if dotted {
            stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
        } else {
            fill(path, with: .color(color))
        }
    }
}
