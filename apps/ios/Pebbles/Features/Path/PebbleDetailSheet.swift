import SwiftUI
import os

/// Post-create viewer sheet.
///
/// Presented by `PathView` after `CreatePebbleSheet` successfully dismisses.
/// Loads the `PebbleDetail` from the DB (now including `render_svg`) and
/// renders `PebbleRenderView` at the top when the render is available,
/// followed by a metadata block and a Done button.
///
/// Distinct from `EditPebbleSheet`: this sheet is a read-only reveal, not a
/// form. Tap-to-view of existing pebbles from the path list remains on
/// `EditPebbleSheet` in slice 1.
struct PebbleDetailSheet: View {
    let pebbleId: UUID

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var detail: PebbleDetail?
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Recorded pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
        .task { await load() }
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
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let svg = detail.renderSvg {
                        PebbleRenderView(svg: svg, strokeColor: detail.emotion.color)
                            .frame(maxWidth: .infinity)
                            .frame(height: 260)
                            .padding(.vertical)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(detail.name).font(.headline)
                        if let description = detail.description, !description.isEmpty {
                            Text(description).font(.body)
                        }
                        Text(detail.happenedAt, style: .date)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if !detail.domains.isEmpty {
                            Text(detail.domains.map(\.name).joined(separator: " · "))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                }
            }
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
                    render_svg, render_version,
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
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
