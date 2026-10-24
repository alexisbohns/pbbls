import SwiftUI

/// A month as a dolmen (#1226, D1): two standing stones under a capstone, in the
/// cairn's box and stones, in one `color`. Decorative: the strip item says the
/// month in words. Android: `DolmenMark`.
struct DolmenMark: View {
    let color: Color

    private static let uprightLength: CGFloat = 20
    private static let uprightThickness: CGFloat = 11
    private static let uprightSpread: CGFloat = 13
    private static let cap = CGSize(width: 29, height: 10)
    private static let capSink: CGFloat = 3

    var body: some View {
        Canvas { context, size in
            guard let stone = FlatStone.medium else { return }
            let length = Self.uprightLength
            let thickness = Self.uprightThickness
            let uprightTop = size.height - length
            // An upright is a stone stood on end: drawn lying, then turned a quarter about its centre.
            for side in [-1.0, 1.0] {
                var upright = context
                upright.translateBy(x: size.width / 2 + side * Self.uprightSpread / 2, y: uprightTop + length / 2)
                upright.rotate(by: .degrees(90))
                upright.drawFlatStone(stone, in: CGRect(x: -length / 2, y: -thickness / 2, width: length,
                                                        height: thickness), color: color)
            }
            context.drawFlatStone(stone, in: CGRect(x: (size.width - Self.cap.width) / 2,
                                                    y: uprightTop - Self.cap.height + Self.capSink,
                                                    width: Self.cap.width, height: Self.cap.height), color: color)
        }
        .frame(width: CairnGeometry.box.width, height: CairnGeometry.box.height)
        .accessibilityHidden(true)
    }
}
