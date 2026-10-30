import SwiftUI

/// Each first-five task drawn as the part it makes (NOTES "Aligned with main",
/// as `ChecklistIllustrations` pictures them): a blank seal, the seal lit, three
/// seals together, a soul's mark, a glyph in a dashed circle. Shared by the
/// wheel and the Explorer sheet's rows.
enum FirstFiveMarks {
    static let soul = ["M30,110 Q65,55 100,110 Q135,165 170,110"]
    static let glyph = ["M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101"]

    /// The marks parsed once.
    static let soulPaths = parse(soul)
    static let glyphPaths = parse(glyph)

    private static func parse(_ strokes: [String]) -> [Path] {
        strokes.compactMap { SVGPathParser.parse($0).map(Path.init) }
    }
}

extension GraphicsContext {
    /// `scale` grows the mark for a sheet's row (1.7 there); its strokes keep their weight.
    func drawFirstFiveMark(_ task: HomeChecklist.Task, at center: CGPoint, scale: CGFloat = 1, inks: AchievementInks) {
        func at(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint { CGPoint(x: center.x + dx * scale, y: center.y + dy * scale) }
        switch task {
        case .moment:
            draw(FlatSeal(size: .medium, outline: .neutral, print: .blank, width: 10 * scale), at: center,
                 color: inks.ink, inks: inks)
        case .pebble:
            draw(FlatSeal(size: .medium, outline: .highlight, print: .solid, width: 10 * scale), at: center,
                 color: inks.ink, inks: inks)
        case .collection:
            draw(FlatSeal(size: .small, outline: .highlight, print: .solid, width: 6 * scale), at: at(-4.6, 2.4),
                 color: inks.ink, inks: inks)
            draw(FlatSeal(size: .medium, outline: .neutral, print: .tint, width: 6.6 * scale), at: at(0, -3),
                 color: inks.ink, inks: inks)
            draw(FlatSeal(size: .small, outline: .lowlight, print: .wash, width: 6 * scale), at: at(4.6, 2.4),
                 color: inks.ink, inks: inks)
        case .soul:
            strokeMark(FirstFiveMarks.soulPaths, side: 14 * scale, at: center, color: inks.ink)
        case .glyph:
            stroke(Path.circle(center, 7.2 * scale), with: .color(inks.ink2),
                   style: StrokeStyle(lineWidth: 1, dash: [1.6, 1.6]))
            strokeMark(FirstFiveMarks.glyphPaths, side: 9.5 * scale, at: center, color: inks.ink)
        }
    }

    /// A mark drawn in a 200-unit box, set `side` wide on `center`, at the glyph's 1.14 pt carve.
    private func strokeMark(_ strokes: [Path], side: CGFloat, at center: CGPoint, color: Color) {
        let transform = CGAffineTransform(translationX: center.x - side / 2, y: center.y - side / 2)
            .scaledBy(x: side / 200, y: side / 200)
        for path in strokes {
            stroke(path.applying(transform), with: .color(color),
                   style: StrokeStyle(lineWidth: 1.14, lineCap: .round, lineJoin: .round))
        }
    }
}
