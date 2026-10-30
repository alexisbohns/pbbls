import SwiftUI

/// One counting stack's skyline (#1306): a stage per ladder step, in ink when
/// the server lists it reached, the next one filled from the ground by the
/// server's progress; the contour in ink once every stage stands. The card
/// draws the pebbles wide and the glyph stacks narrow.
struct PebbleSkyline: View {
    let ladder: AchievementsPage.Ladder
    var box: CGRect = SkylineGeometry.wide
    /// Where the contour comes back to the ground.
    var end: CGFloat = 224
    var width: CGFloat = 300

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let count = min(ladder.ladder.count, SkylineGeometry.stages.count)
        Canvas { context, size in
            var c = context
            let k = size.width / box.width
            c.scaleBy(x: k, y: k)
            c.translateBy(x: -box.minX, y: -box.minY)
            for index in 0..<count {
                let step = ladder.ladder[index]
                if ladder.isReached(step) {
                    c.fill(SkylineGeometry.stage(index), with: .color(inks.ink))
                } else if let next = ladder.next, next.threshold == step, next.progress > 0 {
                    var rising = c
                    rising.clip(to: Path(SkylineGeometry.fill(index, fraction: next.progress)))
                    rising.fill(SkylineGeometry.stage(index), with: .color(inks.ink))
                }
            }
            let standing = (0..<count).allSatisfy { ladder.isReached(ladder.ladder[$0]) }
            c.stroke(SkylineGeometry.contour(stages: count, end: end), with: .color(standing ? inks.ink : inks.ink2),
                     style: StrokeStyle(lineWidth: 1 / k, lineCap: .round, lineJoin: .round))
        }
        .frame(width: width, height: width * box.height / box.width)
        .accessibilityHidden(true)
    }
}

/// One stage alone (the Pebbles sheet's row mark, the board's `stage`): solid
/// when reached, its contour with the fill it has when next, its contour alone
/// when still ahead, on its own ground line, in a 28 pt box.
struct SkylineStage: View {
    let index: Int
    /// 1 reached, the server's progress for the next, 0 ahead.
    let fill: Double
    var scale: CGFloat = 1

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        Canvas { context, size in
            var c = context
            let span = SkylineGeometry.spans[index]
            let top = SkylineGeometry.tops[index]
            let k = min(28 / (span.upperBound - span.lowerBound + 4), 26 / (SkylineGeometry.ground - top + 2)) * scale
            c.translateBy(x: size.width / 2, y: size.height / 2)
            c.scaleBy(x: k, y: k)
            c.translateBy(x: -(span.lowerBound + span.upperBound) / 2, y: -(SkylineGeometry.ground + top) / 2)
            let stage = SkylineGeometry.stage(index)
            if fill >= 1 {
                c.fill(stage, with: .color(inks.ink))
            } else {
                if fill > 0 {
                    var rising = c
                    rising.clip(to: Path(SkylineGeometry.fill(index, fraction: fill)))
                    rising.fill(stage, with: .color(inks.ink))
                }
                c.stroke(stage, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1 / k, lineJoin: .round))
            }
            c.stroke(Path.segment(CGPoint(x: span.lowerBound - 3, y: SkylineGeometry.ground),
                                  CGPoint(x: span.upperBound + 3, y: SkylineGeometry.ground)),
                     with: .color(inks.ink2), lineWidth: 1 / k)
        }
        .frame(width: 28, height: 28)
        .accessibilityHidden(true)
    }
}
