import SwiftUI

/// Ring-and-digit badge representing the user's Ripples level, 44×44 unless
/// told otherwise. See `docs/superpowers/specs/2026-05-15-ripples-design.md` and
/// issue #442 for full color/state semantics. The rings scale from the 44 pt
/// viewBox; below 32 pt the digit goes (You's line says the level, #1382 D17).
struct RippleBadge: View {
    let level: Int
    let activeToday: Bool
    var side: CGFloat = 44

    /// The digit only fits from 32 pt.
    static func showsDigit(side: CGFloat) -> Bool { side >= 32 }

    /// The 2 pt stroke at 44, scaled with the side, never under 1.25.
    static func strokeWidth(side: CGFloat) -> CGFloat { max(1.25, 2 * side / 44) }

    private var clampedLevel: Int { min(max(level, 0), 6) }

    private func tone(forStroke id: Int) -> RippleStrokeTone {
        rippleStrokeTone(strokeId: id, level: clampedLevel, activeToday: activeToday)
    }

    var body: some View {
        ZStack {
            // Draw outermost first so inner rings paint on top.
            RippleStroke6()
                .stroke(tone(forStroke: 6).color, style: stroke)
                .opacity(0.33)
            RippleStroke5()
                .stroke(tone(forStroke: 5).color, style: stroke)
                .opacity(0.33)
            RippleStroke4()
                .stroke(tone(forStroke: 4).color, style: stroke)
                .opacity(0.33)
            RippleStroke3()
                .stroke(tone(forStroke: 3).color, style: stroke)
                .opacity(0.66)
            RippleStroke2()
                .stroke(tone(forStroke: 2).color, style: stroke)
                .opacity(0.66)
            RippleStroke1()
                .stroke(tone(forStroke: 1).color, style: stroke)

            if Self.showsDigit(side: side) {
                Text(verbatim: "\(clampedLevel)")
                    .pebblesFont(.captionEmphasized)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
        }
        .frame(width: side, height: side)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var stroke: StrokeStyle {
        StrokeStyle(lineWidth: Self.strokeWidth(side: side), lineCap: .round)
    }

    /// "Ripple level 3, active today": You's Ripples line speaks it too.
    var accessibilityLabel: LocalizedStringResource {
        activeToday
            ? LocalizedStringResource("Ripple level \(clampedLevel), active today")
            : LocalizedStringResource("Ripple level \(clampedLevel), inactive today")
    }
}

#Preview("All states — light") {
    RipplePreviewGrid()
        .preferredColorScheme(.light)
}

#Preview("All states — dark") {
    RipplePreviewGrid()
        .preferredColorScheme(.dark)
}

private struct RipplePreviewGrid: View {
    var body: some View {
        VStack(spacing: 12) {
            ForEach([true, false], id: \.self) { active in
                HStack(spacing: 8) {
                    Text(verbatim: active ? "active" : "inactive")
                        .font(.caption)
                        .frame(width: 60, alignment: .leading)
                    ForEach(0...6, id: \.self) { level in
                        RippleBadge(level: level, activeToday: active)
                    }
                }
            }
        }
        .padding()
        .background(Color.pebbles.surface)
    }
}
