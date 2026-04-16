import SwiftUI
import os

/// Pushed detail view for a single soul.
///
/// - Shows the pebbles linked to this soul (filtered via `pebble_souls` inner join).
/// - Header = `.navigationTitle(soul.name)`; header stays in sync with `soul` local state
///   so renames reflect without popping the stack (local state is updated in Task 3).
/// - Tapping a pebble opens the existing `EditPebbleSheet`, matching `PathView` UX.
struct SoulDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var soul: Soul
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.soul.detail")

    init(soul: Soul, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._soul = State(initialValue: soul)
    }

    var body: some View {
        content
            .navigationTitle(soul.name)
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
                EditSoulSheet(soul: soul, onSaved: {
                    // Refresh this view's header and the parent list independently;
                    // neither blocks the other.
                    Task { await reloadSoul() }
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
                description: Text("Pebbles you tag with this soul will appear here.")
            )
        } else {
            List(pebbles) { pebble in
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

    private func reloadSoul() async {
        do {
            let refreshed: Soul = try await supabase.client
                .from("souls")
                .select("id, name")
                .eq("id", value: soul.id)
                .single()
                .execute()
                .value
            self.soul = refreshed
        } catch {
            logger.error("soul reload failed: \(error.localizedDescription, privacy: .private)")
            // Leave stale state; next navigation will refresh.
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            // `pebble_souls!inner(soul_id)` forces an inner join so the `.eq`
            // on the join column filters the parent rows. The extra column is
            // tolerated by `Pebble`'s default `Decodable` (extra keys ignored).
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, pebble_souls!inner(soul_id)")
                .eq("pebble_souls.soul_id", value: soul.id)
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
        } catch {
            logger.error("soul pebbles fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        SoulDetailView(soul: Soul(id: UUID(), name: "Preview Soul"), onChanged: {})
            .environment(SupabaseService())
    }
}
