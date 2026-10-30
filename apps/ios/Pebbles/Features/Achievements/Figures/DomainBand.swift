import SwiftUI

/// The domains' mended band (#1306, the board's `shardStrip`): the domains
/// entered in the order they came, inked, then those not yet, dotted; a dot
/// under each new one. It fills the width it is given.
struct DomainBand: View {
    let domains: AchievementsPage.Domains
    var height: CGFloat = 30

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let entered = domains.arrival
        let count = entered.count + domains.notYet.count
        Canvas { c, size in
            let g = ShardBandGeometry(width: size.width, height: height, count: count)
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
        .frame(height: height + 10)
        .accessibilityHidden(true)
    }
}
