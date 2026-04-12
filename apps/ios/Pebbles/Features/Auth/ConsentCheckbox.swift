import SwiftUI

/// A checkbox row with a label like "I accept the Terms of Service", where the
/// "Terms of Service" fragment is visually styled as a link and tapping anywhere
/// on the label text invokes `onLinkTap`. The tap area covers the whole label
/// (not just the link fragment) because SwiftUI `Text` concatenation doesn't
/// expose per-range gestures cleanly — a deliberate simplification.
struct ConsentCheckbox: View {
    @Binding var isChecked: Bool
    let prefix: String
    let linkText: String
    let onLinkTap: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button {
                isChecked.toggle()
            } label: {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(isChecked ? Color.accentColor : Color.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isChecked ? "Checked" : "Unchecked")

            (Text(prefix) + Text(linkText).underline().foregroundColor(.accentColor))
                .font(.subheadline)
                .onTapGesture {
                    onLinkTap()
                }
        }
    }
}

#Preview {
    @Previewable @State var checked = false
    return ConsentCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Terms of Service",
        onLinkTap: { print("link tapped") }
    )
    .padding()
}
