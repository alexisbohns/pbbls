import SwiftUI
import os

/// Profile → Glyphs. Grid of thumbnails; toolbar "+" carves a new glyph.
struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var glyphs: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showCarveSheet = false
    @State private var renaming: Glyph?
    @State private var renameDraft: String = ""
    @State private var renameError: String?

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
            .alert(
                "Rename glyph",
                isPresented: Binding(
                    get: { renaming != nil },
                    set: { if !$0 { renaming = nil } }
                ),
                presenting: renaming
            ) { glyph in
                TextField("Name (optional)", text: $renameDraft)
                    .textInputAutocapitalization(.words)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    Task { await commitRename(glyph) }
                }
            }
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
            VStack(spacing: 0) {
                if let renameError {
                    Text(renameError)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                        .padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(glyphs) { glyph in
                            Button {
                                renameDraft = glyph.name ?? ""
                                renaming = glyph
                            } label: {
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
                            .buttonStyle(.plain)
                            .accessibilityLabel(glyph.name ?? "Untitled glyph")
                            .accessibilityHint("Double tap to rename")
                        }
                    }
                    .padding()
                }
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

    private func commitRename(_ glyph: Glyph) async {
        renameError = nil
        guard let index = glyphs.firstIndex(where: { $0.id == glyph.id }) else { return }
        let original = glyphs[index]
        let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let optimisticName: String? = trimmed.isEmpty ? nil : trimmed

        // Optimistic update
        glyphs[index] = Glyph(
            id: glyph.id,
            name: optimisticName,
            strokes: glyph.strokes,
            viewBox: glyph.viewBox
        )

        do {
            let updated = try await service.updateName(id: glyph.id, name: renameDraft)
            if let i = glyphs.firstIndex(where: { $0.id == updated.id }) {
                glyphs[i] = updated
            }
        } catch {
            logger.error("glyph rename failed: \(error.localizedDescription, privacy: .private)")
            // Revert
            if let i = glyphs.firstIndex(where: { $0.id == glyph.id }) {
                glyphs[i] = original
            }
            renameError = "Couldn't rename glyph. Please try again."
        }
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
    }
}
