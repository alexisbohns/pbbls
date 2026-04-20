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
    @State private var isPresentingOnboarding = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isPresentingOnboarding = true
                        } label: {
                            Image(systemName: "info.circle")
                        }
                        .accessibilityLabel("Show how Pebbles works")
                    }
                }
                .pebblesScreen()
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
        .fullScreenCover(isPresented: $isPresentingOnboarding) {
            OnboardingView(steps: OnboardingSteps.all) {
                // Replay is idempotent — only RootView's initial-gate
                // closure writes @AppStorage("hasSeenOnboarding").
                isPresentingOnboarding = false
            }
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
                    .listRowBackground(Color.pebblesListRow)
                }

                Section("Path") {
                    ForEach(pebbles) { pebble in
                        Button {
                            selectedPebbleId = pebble.id
                        } label: {
                            HStack(spacing: 12) {
                                pebbleThumbnail(for: pebble)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(pebble.name).font(.body)
                                    Text(pebble.happenedAt, style: .date)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func pebbleThumbnail(for pebble: Pebble) -> some View {
        if let svg = pebble.renderSvg {
            PebbleRenderView(svg: svg, strokeColor: pebble.emotion?.color)
                .frame(width: 40, height: 40)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.secondary.opacity(0.15))
                .frame(width: 40, height: 40)
        }
    }

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, render_svg, emotion:emotions(id, slug, name, color)")
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
