import SwiftUI
import os

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var presentedDetailPebbleId: UUID?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                presentedDetailPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            EditPebbleSheet(pebbleId: id, onSaved: {
                Task { await load() }
            })
        }
        .sheet(item: $presentedDetailPebbleId) { id in
            PebbleDetailSheet(pebbleId: id)
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            Text(loadError).foregroundStyle(.secondary)
        } else {
            List {
                Section {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Label("Record a pebble", systemImage: "plus.circle.fill")
                            .font(.headline)
                    }
                }

                Section("Path") {
                    ForEach(pebbles) { pebble in
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

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at")
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
            self.isLoading = false
        } catch {
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }
}

#Preview {
    PathView()
        .environment(SupabaseService())
}
