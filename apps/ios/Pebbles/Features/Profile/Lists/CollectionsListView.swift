import SwiftUI
import os

struct CollectionsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Collection] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pendingDeletion: Collection?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    var body: some View {
        content
            .navigationTitle("Collections")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
            .refreshable { await load() }
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { collection in
                Button("Delete", role: .destructive) {
                    Task { await delete(collection) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the collection and its links are removed.")
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(
                    get: { deleteError != nil },
                    set: { if !$0 { deleteError = nil } }
                ),
                presenting: deleteError
            ) { _ in
                Button("OK", role: .cancel) { deleteError = nil }
            } message: { message in
                Text(message)
            }
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
                systemImage: "square.stack.3d.up",
                description: Text("Your collections will appear here.")
            )
        } else {
            List {
                ForEach(items) { collection in
                    NavigationLink {
                        CollectionDetailView(collection: collection, onChanged: {
                            Task { await load() }
                        })
                    } label: {
                        CollectionRow(collection: collection)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            pendingDeletion = collection
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [Collection] = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
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

    private func delete(_ collection: Collection) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("collections")
                .delete()
                .eq("id", value: collection.id)
                .execute()
            await load()
        } catch {
            logger.error("delete collection failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

/// Row for the collections list. Two lines: name on top, mode badge + count below.
private struct CollectionRow: View {
    let collection: Collection

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(collection.name)
                .font(.body)
            HStack(spacing: 6) {
                CollectionModeBadge(mode: collection.mode)
                if collection.mode != nil {
                    Text("·")
                        .foregroundStyle(.secondary)
                }
                Text(pebbleCountLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var pebbleCountLabel: String {
        switch collection.pebbleCount {
        case 0: return "No pebbles"
        case 1: return "1 pebble"
        default: return "\(collection.pebbleCount) pebbles"
        }
    }
}
