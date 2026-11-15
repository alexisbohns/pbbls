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

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dragStartAngle: Double?
    /// Resets when the touch ends or the system cancels it, so a cancelled
    /// drag never leaves the editing flag or the start angle behind.
    @GestureState private var isDragging = false

    var body: some View {
        VStack(spacing: Spacing.xs) {
            // Figures are always Ysabeau, lining and tabular (D22): the Inclusive zero is slashed.
            Text("\(Int(angle.rounded()))°")
                .spineType(.entryTime)
                .foregroundStyle(Color.pebbles.onSurface)
            ticks
                .frame(height: 44)
                .overlay { needle }
                .contentShape(Rectangle())
                .gesture(drag)
                // The drag claims the touch from minimum distance 0, so the
                // double tap rides along beside it.
                .simultaneousGesture(TapGesture(count: 2).onEnded { reset() })
        }
        .sensoryFeedback(.selection, trigger: Int(angle.rounded())) { _, _ in HapticsPreference.isOn() }
        .onChange(of: isDragging) { _, dragging in
            if !dragging {
                dragStartAngle = nil
                onEditingChanged(false)
            }
        }
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
            .updating($isDragging) { _, state, _ in state = true }
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
    }

    private func reset() {
        if reduceMotion {
            onChange(0)
        } else {
            withAnimation(.snappy) { onChange(0) }
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
                let slot = centreX + CGFloat(degree) * Self.pointsPerDegree
                guard slot > -5, slot < size.width + 5 else { continue }
                var rng = SeededGenerator(seed: "angle-tick-\(degree)")
                let major = degree % 15 == 0, mid = degree % 5 == 0
                let base: CGFloat = degree == 0 ? 30 : major ? 24 : mid ? 18 : 11
                let length = base * (1 + rng.unit() * 0.1)
                let x = slot + rng.unit() * 0.6
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
