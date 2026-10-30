import SwiftUI

/// Consent checkbox: a 28pt rounded square (stock when empty, primary when
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
                .fill(isChecked ? Color.pebbles.primary : Color.pebbles.stock)
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(isChecked ? Color.pebbles.primary : Color.pebbles.outline, lineWidth: 1)

            PbblsIconImage(isChecked ? .checkCircle : .pebbleRound, .tile,
                           style: AnyShapeStyle(isChecked ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant))
        }
        .frame(width: boxSize, height: boxSize)
    }

    private var label: some View {
        (Text(prefix) + Text(linkText).underline().foregroundColor(Color.pebbles.primary))
            .font(.subheadline)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
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
    .background(Color.pebbles.surface)
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
    .background(Color.pebbles.surface)
}
