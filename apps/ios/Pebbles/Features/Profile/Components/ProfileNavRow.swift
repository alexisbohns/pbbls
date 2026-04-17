import SwiftUI

/// A labeled row with a trailing chevron, for triggers that open a sheet
/// rather than push a screen. Collections / Souls / Glyphs don't use this —
/// they use `NavigationLink`, which provides its own chevron.
struct ProfileNavRow: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Label(title, systemImage: systemImage)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    List {
        Section("Legal") {
            ProfileNavRow(title: "Terms", systemImage: "doc.text") {}
            ProfileNavRow(title: "Privacy", systemImage: "lock.shield") {}
        }
    }
}
