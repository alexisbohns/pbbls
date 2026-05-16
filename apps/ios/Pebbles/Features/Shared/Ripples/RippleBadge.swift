import SwiftUI

/// 44×44 ring-and-digit badge representing the user's Ripples level.
/// See `docs/superpowers/specs/2026-05-15-ripples-design.md` and
/// issue #442 for full color/state semantics.
struct RippleBadge: View {
    let level: Int
    let activeToday: Bool

    @Environment(\.colorScheme) private var colorScheme

    private var clampedLevel: Int { min(max(level, 0), 6) }

    private var digitColor: Color {
        switch (colorScheme, activeToday) {
        case (.dark, true):   return .pebblesSurface
        case (.light, true):  return .pebblesForeground
        case (.dark, false),
             (.light, false): return .pebblesMutedForeground
        @unknown default:     return .pebblesForeground
        }
    }

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

            Text(verbatim: "\(clampedLevel)")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(digitColor)
        }
        .frame(width: 44, height: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var stroke: StrokeStyle {
        StrokeStyle(lineWidth: 2, lineCap: .round)
    }

    private var accessibilityLabel: LocalizedStringResource {
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
        .background(Color.pebblesPathBackground)
    }
}
