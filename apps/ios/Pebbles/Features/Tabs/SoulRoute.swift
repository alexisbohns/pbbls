import SwiftUI
import os

/// Shows `PeopleRoute.soul(id)` (#903 §3). The souls list's own rows are the
/// freshest source (a soul created a moment ago is not in reference data until
/// `refreshSouls()` lands); reference data covers a restored scene whose list
/// has not loaded yet. An id found in neither (reference data still loading,
/// or the soul is gone) is fetched as the one `souls` row, the way
/// `CollectionRoute` does: a single-table, single-row select, so no RPC
/// (AGENTS.md). No row, or a failed fetch, logs and pops.
struct SoulRoute: View {
    let id: UUID
    let known: [SoulWithGlyph]
    let onChanged: () -> Void

    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var fetched: RouteResolution<SoulWithGlyph> = .loading

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "tabs.soul-route")

    /// The column list `SoulsListView.load()` reads.
    static let columns = "id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"

    /// The soul from the list's rows, else from reference data; nil when
    /// neither has it yet.
    static func local(
        _ id: UUID,
        known: [SoulWithGlyph],
        refs: ReferenceDataService
    ) -> SoulWithGlyph? {
        known.first(where: { $0.id == id }) ?? refs.souls.first(where: { $0.id == id })
    }

    static func resolution(fetched rows: [SoulWithGlyph]) -> RouteResolution<SoulWithGlyph> {
        rows.first.map { .found($0) } ?? .missing
    }

    /// One source for the detail view, so a fetched soul that later shows
    /// up in reference data keeps the same view identity.
    private var current: SoulWithGlyph? {
        if let soul = Self.local(id, known: known, refs: refs) { return soul }
        if case .found(let soul) = fetched { return soul }
        return nil
    }

    var body: some View {
        Group {
            if let soul = current {
                SoulDetailView(initial: soul, onChanged: onChanged)
            } else if fetched == .missing {
                Color.clear
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .pebblesScreen()
            }
        }
        .task(id: id) { await resolve() }
    }

    private func resolve() async {
        guard Self.local(id, known: known, refs: refs) == nil else { return }
        do {
            let rows: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select(Self.columns)
                .eq("id", value: id)
                .limit(1)
                .execute()
                .value
            fetched = Self.resolution(fetched: rows)
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("soul route fetch failed: \(error.localizedDescription, privacy: .private)")
            fetched = .missing
        }
        if fetched == .missing {
            logger.notice("soul \(id.uuidString, privacy: .private) did not resolve; popping its route")
            dismiss()
        }
    }
}
