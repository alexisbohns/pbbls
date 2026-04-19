import SwiftUI
import os

/// Profile → Glyphs. Grid of thumbnails; toolbar "+" carves a new glyph.
struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var glyphs: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showCarveSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.glyphs")
    private var service: GlyphService { GlyphService(supabase: supabase) }

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    var body: some View {
        content
            .navigationTitle("Glyphs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showCarveSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Carve new glyph")
                }
            }
            .task { await load() }
            .fullScreenCover(isPresented: $showCarveSheet) {
                GlyphCarveSheet(onSaved: { glyph in
                    glyphs.insert(glyph, at: 0)
                })
            }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if glyphs.isEmpty {
            ContentUnavailableView(
                "No glyphs yet",
                systemImage: "scribble",
                description: Text("Tap + to carve your first glyph.")
            )
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(glyphs) { glyph in
                        VStack(spacing: 4) {
                            GlyphThumbnail(
                                strokes: glyph.strokes,
                                side: 96,
                                strokeColor: Color.pebblesAccent
                            )
                            if let name = glyph.name {
                                Text(name)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.glyphs = try await service.list()
        } catch {
            logger.error("glyphs fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
    }
}
