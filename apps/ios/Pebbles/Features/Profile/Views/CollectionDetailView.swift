import SwiftUI
import os

/// Pushed detail view for a single collection.
///
/// - Subheader shows the mode badge + pebble count.
/// - Pebbles are grouped by calendar month (see `groupPebblesByMonth`) with
///   section headers like "April 2026".
/// - Tapping a pebble opens the existing `EditPebbleSheet`.
/// - The Edit toolbar opens `EditCollectionSheet`. After save, the local
///   `collection` is reloaded so the header (title + mode badge + count)
///   stays in sync without popping the stack.
struct CollectionDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var collection: Collection
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collection.detail")

    private static let monthFormatter: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return f
    }()

    init(collection: Collection, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._collection = State(initialValue: collection)
    }

    var body: some View {
        content
            .navigationTitle(collection.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") {
                        isPresentingEdit = true
                    }
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingEdit) {
                EditCollectionSheet(collection: collection, onSaved: {
                    // Refresh this view's header/subheader and the parent list
                    // independently — neither blocks the other.
                    Task { await reloadCollection() }
                    onChanged()
                })
            }
            .sheet(item: $selectedPebbleId) { id in
                EditPebbleSheet(pebbleId: id, onSaved: {
                    Task { await load() }
                })
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load pebbles",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if pebbles.isEmpty {
            ContentUnavailableView(
                "No pebbles yet",
                systemImage: "circle.grid.2x1",
                description: Text("Pebbles added to this collection will appear here.")
            )
        } else {
            List {
                Section {
                    HStack {
                        CollectionModeBadge(mode: collection.mode)
                        Spacer()
                        Text(pebbleCountLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                ForEach(groupedPebbles, id: \.key) { group in
                    Section(header: Text(Self.monthFormatter.string(from: group.key))) {
                        ForEach(group.value) { pebble in
                            Button {
                                selectedPebbleId = pebble.id
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(pebble.name).font(.body)
                                    Text(pebble.happenedAt, style: .date)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private var groupedPebbles: [(key: Date, value: [Pebble])] {
        groupPebblesByMonth(pebbles, calendar: .current)
    }

    private var pebbleCountLabel: String {
        switch pebbles.count {
        case 0: return "No pebbles"
        case 1: return "1 pebble"
        default: return "\(pebbles.count) pebbles"
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            // Inner join on `collection_pebbles` filters parent rows; the extra
            // key is ignored by Pebble's default decoder.
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, collection_pebbles!inner(collection_id)")
                .eq("collection_pebbles.collection_id", value: collection.id)
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
        } catch {
            logger.error("collection pebbles fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func reloadCollection() async {
        do {
            let refreshed: Collection = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
                .eq("id", value: collection.id)
                .single()
                .execute()
                .value
            self.collection = refreshed
        } catch {
            logger.error("collection reload failed: \(error.localizedDescription, privacy: .private)")
            // Leave stale state; next navigation refreshes.
        }
    }
}
