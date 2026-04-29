import SwiftUI
import os

/// Read-view sheet for a single pebble. Loads the `PebbleDetail` from
/// Supabase and renders `PebbleReadView` with a privacy badge + Edit button
/// in the navigation bar. Tapping Edit stacks `EditPebbleSheet` on top; on
/// save the read view reloads in place and notifies the parent.
///
/// Used as the destination for both:
/// - Path-list tap of an existing pebble.
/// - Post-create reveal after `CreatePebbleSheet` completes.
struct PebbleDetailSheet: View {
    let pebbleId: UUID
    var onPebbleUpdated: (() -> Void)?

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var detail: PebbleDetail?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(.hidden, for: .navigationBar)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        if let detail {
                            PebblePrivacyBadge(visibility: detail.visibility, style: .chip)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isPresentingEdit = true
                        } label: {
                            Text("Edit")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.pebblesForeground)
                                .padding(.horizontal, 14)
                                .frame(height: 36)
                                .background(
                                    Capsule().fill(Color.pebblesBackground.opacity(0.85))
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(detail == nil)
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingEdit) {
            EditPebbleSheet(pebbleId: pebbleId, onSaved: {
                Task { await load() }
                onPebbleUpdated?()
            })
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") { Task { await load() } }
            }
        } else if let detail {
            PebbleReadView(detail: detail)
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let loaded: PebbleDetail = try await supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value
            self.detail = loaded
            self.isLoading = false
        } catch {
            logger.error("pebble detail load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

#Preview {
    PebbleDetailSheet(pebbleId: UUID())
        .environment(SupabaseService())
}
