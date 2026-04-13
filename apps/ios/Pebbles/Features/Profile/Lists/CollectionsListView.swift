import SwiftUI
import os

struct CollectionsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [PebbleCollection] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    var body: some View {
        content
            .navigationTitle("Collections")
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
                "Couldn't load collections",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No collections yet",
                systemImage: "tray",
                description: Text("Your collections will appear here.")
            )
        } else {
            List(items) { collection in
                Text(collection.name)
            }
        }
    }

    private func load() async {
        do {
            let result: [PebbleCollection] = try await supabase.client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        CollectionsListView()
            .environment(SupabaseService())
    }
}
