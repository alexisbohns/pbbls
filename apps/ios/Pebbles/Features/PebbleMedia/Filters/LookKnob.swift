import SwiftUI

/// One of the Filter tool's four knobs (intensity, vignette, blur, grain): a tonal
/// disc with a 270° track, the value drawn as a primary arc ending in a dot, the
/// percentage inside and the name below. Drag up or right to turn it up, double-tap
/// to reset (spec 2026-11-23 decision 7); VoiceOver adjusts it in 5 % steps.
struct LookKnob: View {
    let label: LocalizedStringResource
    @Binding var value: Double
    let defaultValue: Double
    var isEnabled = true

    @State private var dragStart: Double?

    static let diameter: CGFloat = 64
    private static let track: CGFloat = 4
    /// The track opens at the bottom, like a hardware dial: 135° to 405°.
    private static let startAngle = 135.0
    private static let sweep = 270.0
    /// Points of drag for the whole range.
    private static let travel = 160.0
    private static let step = 0.05

    var body: some View {
        VStack(spacing: Spacing.xs) {
            dial
                .frame(width: Self.diameter, height: Self.diameter)
                .contentShape(Circle())
                .gesture(drag, including: isEnabled ? .all : .none)
                .simultaneousGesture(TapGesture(count: 2).onEnded { if isEnabled { value = defaultValue } })
                .sensoryFeedback(.selection, trigger: Int((value / Self.step).rounded()))
            Text(label)
                .pebblesFont(.captionEmphasized)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(Text(value, format: .percent.precision(.fractionLength(0))))
        .accessibilityAdjustableAction { direction in
            guard isEnabled else { return }
            switch direction {
            case .increment: value = Self.clamped(value + Self.step)
            case .decrement: value = Self.clamped(value - Self.step)
            @unknown default: break
            }
        }
        .accessibilityAction(named: Text("Reset")) { if isEnabled { value = defaultValue } }
    }

    private var dial: some View {
        let radius = (Self.diameter - Self.track) / 2 - Self.track
        let angle = Angle.degrees(Self.startAngle + Self.sweep * Self.clamped(value))
        return ZStack {
            Circle().fill(Color.pebbles.surfaceContainerHigh)
            arc(to: 1)
                .stroke(Color.pebbles.outlineVariant, style: StrokeStyle(lineWidth: Self.track, lineCap: .round))
            arc(to: Self.clamped(value))
                .stroke(Color.pebbles.primary, style: StrokeStyle(lineWidth: Self.track, lineCap: .round))
            Circle()
                .fill(Color.pebbles.primary)
                .frame(width: Self.track * 2.5, height: Self.track * 2.5)
                .offset(x: radius * cos(angle.radians), y: radius * sin(angle.radians))
            Text(value, format: .percent.precision(.fractionLength(0)))
                .pebblesFont(.captionEmphasized)
                .monospacedDigit()
                .foregroundStyle(Color.pebbles.onSurface)
        }
    }

    private func arc(to fraction: Double) -> some Shape {
        KnobArc(start: .degrees(Self.startAngle), end: .degrees(Self.startAngle + Self.sweep * fraction),
                inset: Self.track * 1.5)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { gesture in
                let start = dragStart ?? value
                dragStart = start
                let delta = (gesture.translation.width - gesture.translation.height) / Self.travel
                value = Self.clamped(start + delta)
            }
            .onEnded { _ in dragStart = nil }
    }

    private static func clamped(_ value: Double) -> Double { min(max(value, 0), 1) }
}

/// An arc of the knob's track, inset from the disc's edge.
private struct KnobArc: Shape {
    let start: Angle
    let end: Angle
    let inset: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addArc(center: CGPoint(x: rect.midX, y: rect.midY), radius: min(rect.width, rect.height) / 2 - inset,
                    startAngle: start, endAngle: end, clockwise: false)
        return path
    }
}
