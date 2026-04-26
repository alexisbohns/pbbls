import SwiftUI
import os

/// Pushed detail view for a single soul.
///
/// - Compact header: 56pt glyph thumbnail + name + pebble count.
/// - Below the header: pebbles tagged with this soul (filtered via
///   `pebble_souls` inner join).
/// - Edit toolbar action presents `EditSoulSheet`. The sheet receives the
///   already-fetched `SoulWithGlyph` so its glyph row renders immediately
///   without a second fetch.
struct SoulDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var soulWithGlyph: SoulWithGlyph
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.soul.detail")

    init(initial: SoulWithGlyph, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._soulWithGlyph = State(initialValue: initial)
    }

    var body: some View {
        content
            .navigationTitle(soulWithGlyph.name)
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
                EditSoulSheet(original: soulWithGlyph, onSaved: {
                    Task { await reloadSoul() }
                    onChanged()
                })
            }
            .sheet(item: $selectedPebbleId) { id in
                EditPebbleSheet(pebbleId: id, onSaved: {
                    Task { await load() }
                })
            }
            .pebblesScreen()
    }

    private var header: some View {
        HStack(spacing: 12) {
            GlyphThumbnail(strokes: soulWithGlyph.glyph.strokes, side: 56)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(soulWithGlyph.name)
                    .font(.headline)
                Text("^[\(pebbles.count) pebbles](inflect: true)")
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
                if pebbles.isEmpty {
                    ContentUnavailableView(
                        "No pebbles yet",
                        systemImage: "circle.grid.2x1",
                        description: Text("Pebbles you tag with this soul will appear here.")
                    )
                    .frame(maxHeight: .infinity)
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
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, pebble_souls!inner(soul_id)")
                .eq("pebble_souls.soul_id", value: soulWithGlyph.id)
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
                )
            ),
            onChanged: {}
        )
        .environment(SupabaseService())
    }
}
