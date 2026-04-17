import SwiftUI

struct BounceExplainerSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("""
                    Bounce is a measure of your momentum over the last 28 \
                    days. It goes from level 0 (quiet) to level 7 (unstoppable).

                    The more days you record a pebble, the higher your Bounce. \
                    Miss a stretch and it eases back down — that's fine. It's \
                    a rhythm, not a score.
                    """)
                }
            }
            .navigationTitle("Bounce")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }
}

#Preview {
    BounceExplainerSheet()
}
