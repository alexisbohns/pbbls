import SwiftUI

/// A knob of the Filter and Adjust tools: a tonal disc with a 270° track, the value
/// drawn as a primary arc ending in a dot, the value inside and the name below. A
/// one-sided knob (0…1) fills from the start of the track; a two-sided one (−1…1)
/// fills from its top-centre zero. The value reads ×100, signed when two-sided.
/// Drag up to turn it up, down to turn it down; a sideways drag is left to the row it
/// sits in, so the Adjust knobs scroll. Double-tap resets (spec 2026-11-23 decision 7);
/// VoiceOver adjusts it in 5 % steps of the range.
struct LookKnob: View {
    let label: LocalizedStringResource
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    let defaultValue: Double
    var isEnabled = true

    /// The value when a vertical drag began; nil between drags and during a sideways one.
    @State private var dragStart: Double?
    /// Decided on a drag's first movement: true when it is the row's, not the knob's.
    @State private var dragIsSideways: Bool?

    static let diameter: CGFloat = 64
    private static let track: CGFloat = 4
    /// The track opens at the bottom, like a hardware dial: 135° to 405°.
    private static let startAngle = 135.0
    private static let sweep = 270.0
    /// Points of drag for the whole range.
    private static let travel = 160.0
    /// A twentieth of the range: one haptic tick, one VoiceOver step.
    private static let steps = 20.0

    private var span: Double { range.upperBound - range.lowerBound }
    private var isTwoSided: Bool { range.lowerBound < 0 }
    private var step: Double { span / Self.steps }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            dial
                .frame(width: Self.diameter, height: Self.diameter)
                .contentShape(Circle())
                // Simultaneous, so a sideways drag still scrolls the row around the knob.
                .simultaneousGesture(drag, including: isEnabled ? .all : .none)
                .simultaneousGesture(TapGesture(count: 2).onEnded { if isEnabled { value = defaultValue } })
                .sensoryFeedback(.selection, trigger: Int((value / step).rounded()))
            Text(label)
                .pebblesFont(.captionEmphasized)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(accessibilityValue)
        .accessibilityAdjustableAction { direction in
            guard isEnabled else { return }
            switch direction {
            case .increment: value = clamped(value + step)
            case .decrement: value = clamped(value - step)
            @unknown default: break
            }
        }
        .accessibilityAction(named: Text("Reset")) { if isEnabled { value = defaultValue } }
    }

    private var dial: some View {
        let position = fraction(of: value)
        let zero = isTwoSided ? fraction(of: 0) : 0
        let radius = Self.diameter / 2 - Self.track * 1.5
        let angle = Angle.degrees(Self.startAngle + Self.sweep * position)
        return ZStack {
            Circle().fill(Color.pebbles.surfaceContainerHigh)
            arc(from: 0, to: 1)
                .stroke(Color.pebbles.outlineVariant, style: StrokeStyle(lineWidth: Self.track, lineCap: .round))
            arc(from: min(zero, position), to: max(zero, position))
                .stroke(Color.pebbles.primary, style: StrokeStyle(lineWidth: Self.track, lineCap: .round))
            Circle()
                .fill(Color.pebbles.primary)
                .frame(width: Self.track * 2.5, height: Self.track * 2.5)
                .offset(x: radius * cos(angle.radians), y: radius * sin(angle.radians))
            Text(verbatim: readout)
                .pebblesFont(.captionEmphasized)
                .monospacedDigit()
                .foregroundStyle(Color.pebbles.onSurface)
        }
    }

    /// The value ×100 as a bare number: "65", or "+20" / "−35" on a two-sided knob.
    private var readout: String {
        let hundredths = Int((value * 100).rounded())
        guard isTwoSided else { return "\(hundredths)" }
        if hundredths > 0 { return "+\(hundredths)" }
        if hundredths < 0 { return "\u{2212}\(-hundredths)" }
        return "0"
    }

    private var accessibilityValue: Text {
        isTwoSided
            ? Text(value, format: .number.precision(.fractionLength(2)).sign(strategy: .always()))
            : Text(value, format: .percent.precision(.fractionLength(0)))
    }

    private func arc(from start: Double, to end: Double) -> some Shape {
        KnobArc(start: .degrees(Self.startAngle + Self.sweep * start),
                end: .degrees(Self.startAngle + Self.sweep * end),
                inset: Self.track * 1.5)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { gesture in
                let sideways = dragIsSideways ?? (abs(gesture.translation.width) > abs(gesture.translation.height))
                dragIsSideways = sideways
                guard !sideways else { return }
                let start = dragStart ?? value
                dragStart = start
                value = clamped(start - gesture.translation.height / Self.travel * span)
            }
            .onEnded { _ in
                dragStart = nil
                dragIsSideways = nil
            }
    }

    private func fraction(of value: Double) -> Double { (clamped(value) - range.lowerBound) / span }
    private func clamped(_ value: Double) -> Double { min(max(value, range.lowerBound), range.upperBound) }
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
