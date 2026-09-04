import SwiftUI

/// Compact grade selector chip (M51) — the native analog of the web composer's
/// VisibilityPicker chip. A Menu-hosted Picker gets the system checkmark on
/// the current grade for free.
struct VisibilityChip: View {
    @Binding var visibility: Visibility

    var body: some View {
        Menu {
            Picker("Privacy", selection: $visibility) {
                ForEach(Visibility.allCases) { grade in
                    Label {
                        Text(grade.label)
                    } icon: {
                        Image(systemName: grade.systemImageName)
                    }
                    .tag(grade)
                }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: visibility.systemImageName)
                    .font(.caption)
                Text(visibility.label)
                    .font(.caption.weight(.medium))
            }
        }
        .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                 comment: "Accessibility label for the composer grade chip"))
        .tint(Color.system.secondary)
    }
}
