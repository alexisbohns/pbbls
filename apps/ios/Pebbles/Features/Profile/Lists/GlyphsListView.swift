import SwiftUI
import os

struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.glyphs")

    var body: some View {
        content
            .navigationTitle("Glyphs")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No glyphs yet",
                systemImage: "scribble",
                description: Text("The glyphs you carve will appear here.")
            )
        } else {
            List(items) { glyph in
                Text(glyph.name ?? "Untitled glyph")
                    .foregroundStyle(glyph.name == nil ? .secondary : .primary)
            }
        }
    }

    private func load() async {
        do {
            let result: [Glyph] = try await supabase.client
                .from("glyphs")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("glyphs fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
    }
}
