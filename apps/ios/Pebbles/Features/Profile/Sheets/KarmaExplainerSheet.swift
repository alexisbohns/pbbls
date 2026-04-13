import SwiftUI

struct KarmaExplainerSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("""
                    Karma reflects the energy you put into your path. \
                    Every pebble you record, every soul you tend, every glyph \
                    you draw — they all contribute.

                    Your Karma grows as you show up for yourself.
                    """)
                }
            }
            .navigationTitle("Karma")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    KarmaExplainerSheet()
}
