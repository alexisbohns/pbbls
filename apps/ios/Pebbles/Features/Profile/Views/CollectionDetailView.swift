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
    @State private var moments: [MomentSummary] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedMomentId: UUID?
    @State private var isPresentingEdit = false
    @State private var pendingDeletion: MomentSummary?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collection.detail")

    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return formatter
    }()

    init(collection: Collection, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._collection = State(initialValue: collection)
    }

    var body: some View {
        content
            .pebblesToolbarTitle(collection.name)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton("Edit") {
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
            .sheet(item: $selectedMomentId) { id in
                EditPebbleSheet(momentId: id, onSaved: {
                    Task { await load() }
                })
            }
            .confirmationDialog(
                pendingDeletion.map { "Delete \(MomentName.display($0.name) ?? String(localized: "Untitled"))?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { moment in
                Button("Delete", role: .destructive) {
                    TapHaptics.play(.warning)
                    Task { await delete(moment) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("This can't be undone.")
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
            .pebblesScreen()
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
        } else if moments.isEmpty {
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
                    .pebblesListRow(position: .only)
                }

                ForEach(groupedPebbles, id: \.key) { group in
                    Section {
                        ForEach(Array(group.value.enumerated()), id: \.element.id) { index, moment in
                            PebbleRow(
                                moment: moment,
                                onTap: { selectedMomentId = moment.id },
                                onDelete: { pendingDeletion = moment }
                            )
                            .pebblesListRow(position: pebblesRowPosition(index: index, count: group.value.count))
                        }
                    } header: {
                        Text(Self.monthFormatter.string(from: group.key)).pebblesSectionHeader()
                    }
                }
            }
            .pebblesList()
        }
    }

    private var groupedPebbles: [(key: Date, value: [MomentSummary])] {
        groupPebblesByMonth(moments, calendar: .current)
    }

    private var pebbleCountLabel: String {
        switch moments.count {
        case 0: return "No pebbles"
        case 1: return "1 pebble"
        default: return "\(moments.count) pebbles"
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.moments = try await MomentsService(client: supabase.client)
                .moments(inCollection: collection.id)
        } catch {
            logger.error("collection moments fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func delete(_ moment: MomentSummary) async {
        pendingDeletion = nil
        do {
            try await MomentDeletion.deleteMoment(moment.id, client: supabase.client)
            await load()
        } catch {
            logger.error("delete moment failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
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
