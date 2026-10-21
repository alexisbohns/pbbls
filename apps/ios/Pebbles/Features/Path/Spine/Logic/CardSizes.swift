import CoreGraphics

/// A card's width, approximate height and height cap, in points.
struct CardSize: Equatable {
    let width: CGFloat
    let height: CGFloat
    let cap: CGFloat
}

/// Deck-card sizing (#1130 R4), a port of `apps/web/lib/utils/spine/card-size.ts`
/// (phone caps). The chrome counts as 123 pt vertical and 95 horizontal:
/// `W = min(column, (cap − 123) × ratio + 95)`, `H = 123 + (W − 95) / ratio`, with
/// a 180 pt floor a title needs. Exact only at u = 4.4; narrower cards drift by up
/// to about 10 pt, as the rule set accepts.
enum CardSizes {
    static let chromeVertical: CGFloat = 123
    static let chromeHorizontal: CGFloat = 95
    static let minWidth: CGFloat = 180
    /// The ratio a card holds until its photo's is known.
    static let pendingRatio: CGFloat = 1

    private static let caps: [Int: CGFloat] = [1: 330, 2: 370, 3: 400]
    private static let edges: [Int: CGFloat] = [1: 2, 2: 3.5, 3: 5.5]

    static func cap(_ intensity: Int) -> CGFloat {
        caps[intensity] ?? 370
    }

    /// The painted bottom edge's thickness.
    static func edge(_ intensity: Int) -> CGFloat {
        edges[intensity] ?? 3.5
    }

    static func cardSize(intensity: Int, ratio: CGFloat?, column: CGFloat) -> CardSize {
        let cap = Self.cap(intensity)
        let safe = ratio.flatMap { $0.isFinite && $0 > 0 ? $0 : nil } ?? pendingRatio
        let fitted = min(column, (cap - chromeVertical) * safe + chromeHorizontal)
        let width = max(fitted, min(minWidth, column))
        return CardSize(width: width, height: chromeVertical + (width - chromeHorizontal) / safe, cap: cap)
    }
}
