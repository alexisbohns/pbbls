import SwiftUI

/// The flow's top bar: back chevron, progress dots, close.
///
/// Minimal by design (D2) — picking is the advance, so there is no Next button
/// competing with the dots, and "Save as draft" lives in the close
/// confirmation rather than taking permanent residence here (D9).
struct RecordFlowChrome: View {
    let step: RecordStep
    let onBack: () -> Void
    let onClose: () -> Void

    private var canGoBack: Bool { step.previous != nil }

    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.system.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .opacity(canGoBack ? 1 : 0)
            .disabled(!canGoBack)
            .accessibilityLabel("Back")
            .accessibilityHidden(!canGoBack)

            Spacer()

            dots

            Spacer()

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.system.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, Spacing.sm)
    }

    /// One element to VoiceOver, not ten: "Step 4 of 10" is the useful reading,
    /// and ten unlabeled dots is not.
    private var dots: some View {
        HStack(spacing: Spacing.xs + 2) {
            ForEach(RecordStep.counted) { candidate in
                Circle()
                    .fill(fill(for: candidate))
                    .frame(width: 6, height: 6)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stepAnnouncement)
    }

    private func fill(for candidate: RecordStep) -> Color {
        guard let current = step.dotIndex, let index = candidate.dotIndex else {
            return Color.system.muted
        }
        return index <= current ? Color.accent.primary : Color.system.muted
    }

    private var stepAnnouncement: Text {
        guard let index = step.dotIndex else { return Text("Done") }
        return Text("Step \(index + 1) of \(RecordStep.counted.count)")
    }
}
