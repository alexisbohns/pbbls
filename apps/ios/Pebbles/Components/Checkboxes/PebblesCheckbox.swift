import SwiftUI

/// Consent checkbox: a 28pt rounded square (white when empty, accent when
/// checked) followed by a label that contains a tappable link fragment.
/// Tap area on the box is at least 44×44pt regardless of the visual size,
/// per HIG. The label tap fires `onLinkTap` (whole-label, since per-range
/// gestures don't compose cleanly on `Text` concatenation).
struct PebblesCheckbox: View {
    @Binding var isChecked: Bool
    let prefix: LocalizedStringResource
    let linkText: LocalizedStringResource
    let onLinkTap: () -> Void

    @ScaledMetric(relativeTo: .body) private var boxSize: CGFloat = 44
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 12

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            box
                .contentShape(Rectangle())
                .onTapGesture { isChecked.toggle() }

            label
                .onTapGesture { onLinkTap() }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityAddTraits(isChecked ? .isSelected : [])
        .accessibilityAction(named: Text(linkText), onLinkTap)
    }

    private var box: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(isChecked ? Color.pebblesAccent : Color.white)
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(isChecked ? Color.pebblesAccent : Color.pebblesBorder, lineWidth: 1)

            Image(systemName: isChecked ? "checkmark.square" : "square")
                .font(.title3)
                .foregroundStyle(isChecked ? Color.pebblesBackground : Color.pebblesMutedForeground)
        }
        .frame(width: boxSize, height: boxSize)
    }

    private var label: some View {
        (Text(prefix) + Text(linkText).underline().foregroundColor(Color.pebblesAccent))
            .font(.subheadline)
            .foregroundStyle(Color.pebblesMutedForeground)
    }
}

#Preview("Unchecked") {
    @Previewable @State var checked = false
    return PebblesCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Terms of Service",
        onLinkTap: {}
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Checked") {
    @Previewable @State var checked = true
    return PebblesCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Privacy Policy",
        onLinkTap: {}
    )
    .padding()
    .background(Color.pebblesBackground)
}
