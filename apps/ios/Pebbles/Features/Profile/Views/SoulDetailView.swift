import SwiftUI
import os

/// Pushed detail view for a single soul.
///
/// - Compact header: 56pt glyph thumbnail + name + pebble count.
/// - Below the header: moments whose pebble carries this soul
///   (`MomentsService.moments(taggedWithSoul:)`).
/// - Edit toolbar action presents `EditSoulSheet`. The sheet receives the
///   already-fetched `SoulWithGlyph` so its glyph row renders immediately
///   without a second fetch.
struct SoulDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var soulWithGlyph: SoulWithGlyph
    @State private var moments: [MomentSummary] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedMomentId: UUID?
    @State private var isPresentingEdit = false
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
    /// Ignores a second delete confirmed while the first is still running.
    @State private var isDeleting = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.soul.detail")

    init(initial: SoulWithGlyph, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._soulWithGlyph = State(initialValue: initial)
    }

    var body: some View {
        content
            .pebblesToolbarTitle(soulWithGlyph.name)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton("Edit") {
                        isPresentingEdit = true
                    }
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingEdit) {
                EditSoulSheet(original: soulWithGlyph, onSaved: {
                    Task { await reloadSoul() }
                    onChanged()
                })
            }
            // R9: a row opens the moment's read page, not the edit form.
            .sheet(item: $selectedMomentId) { id in
                PebbleDetailSheet(momentId: id, onPebbleUpdated: {
                    Task { await load() }
                    onChanged()
                })
            }
            .momentDeleteDialog(target: $pendingDeletion) { target, option in
                Task { await delete(option, on: target) }
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

    private var header: some View {
        HStack(spacing: 12) {
            GlyphView(case: .default, strokes: soulWithGlyph.glyph.strokes, side: 56)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(soulWithGlyph.name)
                    .pebblesFont(.bodyLeadHand)
                Text("^[\(moments.count) pebbles](inflect: true)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
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
        } else {
            VStack(spacing: 0) {
                header
                if moments.isEmpty {
                    ContentUnavailableView(
                        "No pebbles yet",
                        systemImage: "circle.grid.2x1",
                        description: Text("Pebbles you tag with this soul will appear here.")
                    )
                    .frame(maxHeight: .infinity)
                } else {
                    List {
                        ForEach(Array(moments.enumerated()), id: \.element.id) { index, moment in
                            PebbleRow(
                                moment: moment,
                                onTap: { selectedMomentId = moment.id },
                                onDelete: { pendingDeletion = MomentDeleteTarget(summary: moment) }
                            )
                            .pebblesListRow(position: pebblesRowPosition(index: index, count: moments.count))
                        }
                    }
                    .pebblesList()
                }
            }
        }
    }

    private func reloadSoul() async {
        do {
            let refreshed: SoulWithGlyph = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .eq("id", value: soulWithGlyph.id)
                .single()
                .execute()
                .value
            self.soulWithGlyph = refreshed
        } catch {
            logger.error("soul reload failed: \(error.localizedDescription, privacy: .private)")
            // Leave stale state; next navigation will refresh.
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.moments = try await MomentsService(client: supabase.client)
                .moments(taggedWithSoul: soulWithGlyph.id)
        } catch {
            logger.error("soul moments fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    /// Either delete drops the row: the list only carries moments with a
    /// pebble tagged here, so a reload after `delete_pebble` leaves it out.
    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        guard !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await MomentDeletion.perform(option, on: target, client: supabase.client)
            await load()
            onChanged()
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }
}

#Preview {
    NavigationStack {
        SoulDetailView(
            initial: SoulWithGlyph(
                id: UUID(),
                name: "Preview Soul",
                glyphId: SystemGlyph.default,
                glyph: Glyph(
                    id: SystemGlyph.default,
                    name: nil,
                    strokes: [],
                    viewBox: "0 0 200 200",
                    userId: nil
                ),
                pebblesCount: 0
            ),
            onChanged: {}
        )
        .environment(SupabaseService())
    }
}
