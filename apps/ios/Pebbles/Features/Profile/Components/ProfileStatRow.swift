import SwiftUI

/// One row in the Profile screen's Stats section. Displays a label and an
/// optional integer value; taps trigger the provided action (used to open an
/// explainer sheet). Shows an em-dash when `value` is nil so the row keeps
/// its layout while stats are loading or have failed to load.
struct ProfileStatRow: View {
    let title: String
    let systemImage: String
    let value: Int?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Label(title, systemImage: systemImage)
                Spacer()
                Text(valueText)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var valueText: String {
        if let value { return String(value) }
        return "—"
    }
}

#Preview {
    List {
        Section("Stats") {
            ProfileStatRow(title: "Karma", systemImage: "sparkles", value: 128) {}
            ProfileStatRow(title: "Bounce", systemImage: "arrow.up.right", value: nil) {}
        }
    }
}
