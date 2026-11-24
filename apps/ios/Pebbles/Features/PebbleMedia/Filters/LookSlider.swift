import SwiftUI

/// One labelled slider of the Filter and Adjust tools: a readout, double-tap to reset
/// to its default (spec 2026-11-23 decision 7), and a "Reset" accessibility action.
struct LookSlider: View {
    enum Readout { case percent, signed }

    let label: LocalizedStringResource
    @Binding var value: Double
    let range: ClosedRange<Double>
    let defaultValue: Double
    let readout: Readout
    var isEnabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack {
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurface)
                Spacer()
                readoutText
                    .pebblesFont(.subhead)
                    .monospacedDigit()
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .accessibilityHidden(true)
            Slider(value: $value, in: range)
                .tint(Color.pebbles.primary)
                .disabled(!isEnabled)
                // Simultaneous, so the double-tap resets without stealing the drag.
                .simultaneousGesture(TapGesture(count: 2).onEnded { value = defaultValue })
                .accessibilityLabel(Text(label))
                .accessibilityValue(readoutText)
                .accessibilityAction(named: Text("Reset")) { value = defaultValue }
        }
        .opacity(isEnabled ? 1 : 0.5)
    }

    private var readoutText: Text {
        switch readout {
        case .percent: Text(value, format: .percent.precision(.fractionLength(0)))
        case .signed: Text(value, format: .number.precision(.fractionLength(2)).sign(strategy: .always()))
        }
    }
}
