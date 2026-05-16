import SwiftUI

/// Placeholder presented from ProfileView's gear button until issue #452 lands.
struct SettingsStubSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Image(systemName: "gear")
                    .font(.system(size: 48))
                    .foregroundStyle(Color.pebblesMutedForeground)
                Text("Settings — coming in #452")
                    .font(.subheadline)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }
}
