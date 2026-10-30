import SwiftUI

/// The domains' mended band (#1306, the board's `shardStrip`): the domains
/// entered in the order they came, inked, then those not yet, dotted; a dot
/// under each new one. It keeps the board's box, 300 by 40 (the band 30 high,
/// room for the dots under it), and scales as a whole up to its board width,
/// as Android and web draw it: never a stretched band at a fixed height.
struct DomainBand: View {
    let domains: AchievementsPage.Domains

    /// The board's box, the band inside it, and where the dots sit.
    static let box = CGSize(width: 300, height: 40)
    static let bandHeight: CGFloat = 30

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let entered = domains.arrival
        let count = entered.count + domains.notYet.count
        Canvas { context, size in
            var c = context
            let k = size.width / Self.box.width
            c.scaleBy(x: k, y: k)
            let g = ShardBandGeometry(width: Self.box.width, height: Self.bandHeight, count: count)
            let paper = c.mixed(inks.ink, 0.08, over: inks.stock)
            for index in 0..<count {
                let shard = Path.polygon(g.shard(index))
                if index < entered.count {
                    c.fill(shard, with: .color(paper))
                    c.stroke(shard, with: .color(inks.ink), style: StrokeStyle(lineWidth: 1.3, lineJoin: .round))
                } else {
                    c.stroke(shard, with: .color(inks.ink2),
                             style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [1.2, 2.2]))
                }
            }
            for (index, entry) in entered.enumerated() where entry.isNew {
                let dot = Path.circle(g.dot(index), 2.4)
                c.stroke(dot, with: .color(inks.stock), lineWidth: 1.6)
                c.fill(dot, with: .color(inks.ink))
            }
        }
        .aspectRatio(Self.box.width / Self.box.height, contentMode: .fit)
        .frame(maxWidth: Self.box.width)
        .accessibilityHidden(true)
    }
}
