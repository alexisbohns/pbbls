import SwiftUI
import os

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
                .navigationTitle(detail?.name ?? "Pebble")
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
                Button("Retry") {
                    Task { await load() }
                }
            }
            .padding()
        } else if let detail {
            loadedForm(detail)
        }
    }

    @ViewBuilder
    private func loadedForm(_ detail: PebbleDetail) -> some View {
        Form {
            Section {
                LabeledContent("When", value: detail.happenedAt.formatted(date: .abbreviated, time: .shortened))
                if let description = detail.description, !description.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Description")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(description)
                    }
                }
            }

            Section("Mood") {
                LabeledContent("Emotion") {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color(hex: detail.emotion.color) ?? .secondary)
                            .frame(width: 10, height: 10)
                        Text(detail.emotion.name)
                    }
                }
                if !detail.domains.isEmpty {
                    LabeledContent("Domain", value: detail.domains.map(\.name).joined(separator: ", "))
                }
                LabeledContent("Valence", value: detail.valence.label)
            }

            if !detail.souls.isEmpty || !detail.collections.isEmpty {
                Section("Optional") {
                    if !detail.souls.isEmpty {
                        LabeledContent("Soul", value: detail.souls.map(\.name).joined(separator: ", "))
                    }
                    if !detail.collections.isEmpty {
                        LabeledContent("Collection", value: detail.collections.map(\.name).joined(separator: ", "))
                    }
                }
            }

            Section("Privacy") {
                LabeledContent("Privacy", value: detail.visibility.label)
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let fetched: PebbleDetail = try await supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value
            self.detail = fetched
            self.isLoading = false
        } catch {
            logger.error("pebble detail fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

// Small helper so we can render the emotion color string ("#RRGGBB") as a SwiftUI Color.
// Returns nil on malformed input; the view falls back to `.secondary`.
private extension Color {
    init?(hex: String) {
        var sanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if sanitized.hasPrefix("#") { sanitized.removeFirst() }
        guard sanitized.count == 6, let value = UInt32(sanitized, radix: 16) else { return nil }
        let red = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue = Double(value & 0xFF) / 255.0
        self = Color(red: red, green: green, blue: blue)
    }
}

#Preview {
    PebbleDetailSheet(pebbleId: UUID())
        .environment(SupabaseService())
}
