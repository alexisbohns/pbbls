import SwiftUI
import os

/// Shows `CollectionsRoute.collection(id)` (#903 §3). Uses the list's row when
/// it has one. Otherwise (a restored scene, or a jump from the You hub) it
/// fetches the one `collections` row by id. That is a single-table,
/// single-row select, so no RPC (AGENTS.md). No row, or a failed fetch, logs
/// and pops.
struct CollectionRoute: View {
    let id: UUID
    let known: Collection?
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var resolution: RouteResolution<Collection>

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "tabs.collection-route")

    /// The column list `CollectionDetailView.reloadCollection()` reads.
    static let columns = "id, name, mode, glyph_id, glyph:glyphs(id, name, strokes, view_box, user_id), "
        + "pebble_count:collection_pebbles(count)"

    init(id: UUID, known: Collection?, onChanged: @escaping () -> Void) {
        self.id = id
        self.known = known
        self.onChanged = onChanged
        self._resolution = State(initialValue: known.map { .found($0) } ?? .loading)
    }

    static func resolution(fetched rows: [Collection]) -> RouteResolution<Collection> {
        rows.first.map { .found($0) } ?? .missing
    }

    var body: some View {
        Group {
            switch resolution {
            case .found(let collection):
                CollectionDetailView(collection: collection, onChanged: onChanged)
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .pebblesScreen()
            case .missing:
                Color.clear
            }
        }
        .task(id: id) { await resolve() }
    }

    private func resolve() async {
        if case .found = resolution { return }
        do {
            let rows: [Collection] = try await supabase.client
                .from("collections")
                .select(Self.columns)
                .eq("id", value: id)
                .limit(1)
                .execute()
                .value
            resolution = Self.resolution(fetched: rows)
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("collection route fetch failed: \(error.localizedDescription, privacy: .private)")
            resolution = .missing
        }
        if resolution == .missing {
            logger.notice("collection \(id.uuidString, privacy: .private) did not resolve; popping its route")
            dismiss()
        }
    }
}
