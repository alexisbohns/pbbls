import SwiftUI

/// The straighten ruler (spec "Tool panel · Rotate"): −45…45°, ticks drawn by
/// hand under a fixed needle. A haptic per whole degree, a snap at 0°,
/// double-tap to return to 0°. Adjustable for VoiceOver, 1° per swipe.
struct AngleRuler: View {
    let angle: Double
    let onChange: (Double) -> Void
    let onEditingChanged: (Bool) -> Void

    private static let pointsPerDegree: CGFloat = 8
    private static let snapWithin: Double = 0.75

    @State private var dragStartAngle: Double?

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text("\(Int(angle.rounded()))°")
                .pebblesFont(.subhead)
                .monospacedDigit()
                .foregroundStyle(Color.pebbles.onSurface)
            ticks
                .frame(height: 36)
                .overlay { needle }
                .contentShape(Rectangle())
                .gesture(drag)
                .onTapGesture(count: 2) { onChange(0) }
        }
        .sensoryFeedback(.selection, trigger: Int(angle.rounded())) { _, _ in HapticsPreference.isOn() }
        .accessibilityElement()
        .accessibilityLabel(Text("Angle"))
        .accessibilityValue(Text("\(Int(angle.rounded())) degrees"))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onChange(min(angle.rounded() + 1, CropGeometry.maxAngle))
            case .decrement: onChange(max(angle.rounded() - 1, -CropGeometry.maxAngle))
            @unknown default: break
            }
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let start = dragStartAngle ?? angle
                if dragStartAngle == nil {
                    dragStartAngle = start
                    onEditingChanged(true)
                }
                // Ticks slide under the needle: dragging left raises the angle.
                var next = start - Double(value.translation.width / Self.pointsPerDegree)
                next = min(max(next, -CropGeometry.maxAngle), CropGeometry.maxAngle)
                if abs(next) < Self.snapWithin { next = 0 }
                onChange(next)
            }
            .onEnded { _ in
                dragStartAngle = nil
                onEditingChanged(false)
            }
    }

    private var needle: some View {
        Capsule()
            .fill(Color.pebbles.primary)
            .frame(width: 3, height: 40)
    }

    private var ticks: some View {
        Canvas { context, size in
            let centreX = size.width / 2 - CGFloat(angle) * Self.pointsPerDegree
            for degree in -45...45 {
                var rng = SeededGenerator(seed: "angle-tick-\(degree)")
                let major = degree % 15 == 0, mid = degree % 5 == 0
                let base: CGFloat = degree == 0 ? 30 : major ? 24 : mid ? 18 : 11
                let length = base * (1 + rng.unit() * 0.1)
                let x = centreX + CGFloat(degree) * Self.pointsPerDegree + rng.unit() * 0.6
                guard x > -4, x < size.width + 4 else { continue }
                let lean = rng.unit() * 0.9
                let bottom = size.height / 2 + length / 2
                var tick = Path()
                tick.move(to: CGPoint(x: x - lean, y: bottom))
                tick.addLine(to: CGPoint(x: x + lean, y: bottom - length))
                let colour = degree == 0 || major ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant
                context.stroke(tick, with: .color(colour),
                               style: StrokeStyle(lineWidth: degree == 0 ? 2.5 : mid ? 1.8 : 1.3, lineCap: .round))
            }
        }
        // Only the alpha matters to a mask; the role keeps feature code literal-free.
        .mask(LinearGradient(colors: [.clear, Color.pebbles.onSurface, Color.pebbles.onSurface, .clear],
                             startPoint: .leading, endPoint: .trailing))
    }
}
